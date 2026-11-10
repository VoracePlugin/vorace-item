package fr.vorace.item.forge;

import fr.vorace.item.Reglages;
import fr.vorace.item.Textes;
import fr.vorace.item.VoraceItem;
import fr.vorace.item.combat.Usure;
import fr.vorace.item.definitions.ModeleObjet;
import fr.vorace.item.exemplaire.Calcul;
import fr.vorace.item.exemplaire.Exemplaire;
import fr.vorace.item.exemplaire.Exemplaires.Signature;
import fr.vorace.item.interfaces.EcouteurMenus;
import fr.vorace.item.interfaces.Menu;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * L'atelier : réparer un objet, l'améliorer avec une pierre d'amélioration, y sertir une gemme. Le joueur pose
 * l'objet dans la case de gauche et, s'il en faut une, la gemme ou la pierre dans la case de droite.
 */
public final class Atelier {

    private static final int OBJET = 11, COMPOSANT = 15, INFO = 13, REPARER = 29, AMELIORER = 31, SERTIR = 33;

    private final VoraceItem plugin;

    public Atelier(VoraceItem plugin) {
        this.plugin = plugin;
    }

    public void ouvrir(Player p) {
        Menu menu = new Menu("Atelier", 5);
        menu.libre(OBJET).libre(COMPOSANT);
        menu.bouton(OBJET - 9, Menu.icone("ANVIL", "<white>Objet", "Pose ici l'objet à réparer,", "à améliorer ou à sertir."), null);
        menu.bouton(COMPOSANT - 9, Menu.icone("AMETHYST_SHARD", "<white>Gemme ou pierre", "Pose ici la gemme à sertir", "ou la pierre d'amélioration."), null);
        menu.bouton(44, Menu.icone("BARRIER", Menu.NON + "Fermer"), (j, d, s) -> j.closeInventory());
        menu.quandChange(() -> boutons(menu, p));
        boutons(menu, p);
        menu.fond("GRAY_STAINED_GLASS_PANE");
        menu.ouvrir(p);
    }

    /** Refait les trois boutons selon ce qui est posé dans les cases. */
    private void boutons(Menu menu, Player p) {
        Inventory inv = menu.getInventory();
        Reglages r = plugin.reglages();
        ItemStack objet = inv.getItem(OBJET);
        Exemplaire ex = plugin.exemplaires().lire(objet);
        ModeleObjet m = ex == null ? null : plugin.catalogue().modele(ex.modele);
        ItemStack composant = inv.getItem(COMPOSANT);
        Exemplaire exComposant = plugin.exemplaires().lire(composant);
        ModeleObjet mComposant = exComposant == null ? null : plugin.catalogue().modele(exComposant.modele);
        boolean objetValide = m != null && !m.empilable();

        if (!objetValide) {
            menu.bouton(INFO, Menu.icone("PAPER", "<white>Atelier", "Réparer : remet la durabilité au maximum.", "Chaque réparation use un peu l'objet pour de bon.", "",
                "Améliorer : avec une pierre d'amélioration,", "les stats de l'objet montent (+1, +2...).", "", "Sertir : pose une gemme dans un emplacement libre."), null);
        } else {
            List<String> infos = new ArrayList<>();
            if (ex.aDurabilite()) {
                infos.add("Durabilité : <white>" + ex.durabilite + " / " + ex.durabiliteMax + Menu.DISCRET + " (à l'origine " + ex.durabiliteOrigine + ")");
                infos.add("Réparations : <white>" + ex.reparations);
            }
            infos.add("Amélioration : <white>+" + ex.amelioration + Menu.DISCRET + " / +" + r.ameliorationMax);
            infos.add("Gemmes : <white>" + ex.gemmes.size() + " / " + Calcul.emplacementsGemmes(plugin.catalogue(), m, ex));
            menu.bouton(INFO, Menu.icone("SPYGLASS", "<white>État de l'objet", infos.toArray(new String[0])), null);
        }

        // réparer
        List<String> rep = new ArrayList<>();
        boolean peutReparer = false;
        if (!objetValide) rep.add(Menu.DISCRET + "Pose un objet à gauche.");
        else if (!ex.aDurabilite()) rep.add(Menu.DISCRET + "Cet objet ne s'use pas.");
        else if (ex.durabilite >= ex.durabiliteMax) rep.add(Menu.DISCRET + "Cet objet n'est pas abîmé.");
        else if (ex.durabiliteMax <= Math.ceil(Math.max(ex.durabiliteOrigine, ex.durabiliteMax) * r.plancherReparation)) rep.add(Menu.NON + "Trop usé : il ne se répare plus.");
        else {
            peutReparer = true;
            rep.add("Remet la durabilité à son maximum.");
            rep.add(Menu.DISCRET + "L'objet perd un peu de durabilité");
            rep.add(Menu.DISCRET + "maximale à chaque réparation.");
            rep.add("");
            rep.add(Menu.clic("Clic", "réparer"));
        }
        menu.bouton(REPARER, Menu.icone(peutReparer ? "ANVIL" : "CHIPPED_ANVIL", (peutReparer ? Menu.OUI : "<white>") + "Réparer", rep.toArray(new String[0])),
            peutReparer ? (j, d, s) -> reparer(menu, j) : null);

        // améliorer
        List<String> am = new ArrayList<>();
        boolean peutAmeliorer = false;
        boolean pierre = mComposant != null && mComposant.type().estPierre();
        if (!objetValide) am.add(Menu.DISCRET + "Pose un objet à gauche.");
        else if (ex.amelioration >= r.ameliorationMax) am.add(Menu.DISCRET + "Cet objet est au niveau maximum.");
        else if (!pierre) am.add(Menu.DISCRET + "Pose une pierre d'amélioration à droite.");
        else {
            peutAmeliorer = true;
            am.add("Passe l'objet à <white>+" + (ex.amelioration + 1) + Menu.TEXTE + " : stats +" + Textes.nombre(r.ameliorationBonus * 100) + " % par niveau.");
            am.add("Chance de réussite : <white>" + Textes.nombre(r.chanceAmelioration(ex.amelioration + 1) * 100) + " %");
            am.add(Menu.DISCRET + "La pierre est consommée dans tous les cas.");
            if (r.ameliorationEchecBaisse) am.add(Menu.NON + "En cas d'échec, l'objet perd un niveau.");
            am.add("");
            am.add(Menu.clic("Clic", "améliorer"));
        }
        menu.bouton(AMELIORER, Menu.icone("NETHER_STAR", (peutAmeliorer ? Menu.OUI : "<white>") + "Améliorer", am.toArray(new String[0])),
            peutAmeliorer ? (j, d, s) -> ameliorer(menu, j) : null);

        // sertir
        List<String> se = new ArrayList<>();
        boolean peutSertir = false;
        boolean gemme = mComposant != null && mComposant.type().estGemme();
        int libres = objetValide ? Calcul.emplacementsGemmes(plugin.catalogue(), m, ex) - ex.gemmes.size() : 0;
        boolean peutRetirer = objetValide && r.gemmeRetirable && !ex.gemmes.isEmpty() && (composant == null || composant.getType().isAir());
        if (!objetValide) se.add(Menu.DISCRET + "Pose un objet à gauche.");
        else if (peutRetirer) {
            se.add("Retire la dernière gemme sertie");
            se.add("et te la rend.");
            se.add("");
            se.add(Menu.clic("Clic", "retirer la gemme"));
        } else if (libres <= 0) se.add(Menu.DISCRET + "Aucun emplacement de gemme libre.");
        else if (!gemme) se.add(Menu.DISCRET + "Pose une gemme à droite.");
        else {
            peutSertir = true;
            se.add("Pose la gemme dans un emplacement libre.");
            se.add(r.gemmeRetirable ? Menu.DISCRET + "Elle pourra être retirée ici." : Menu.DISCRET + "Une gemme sertie ne se retire plus.");
            se.add("");
            se.add(Menu.clic("Clic", "sertir"));
        }
        menu.bouton(SERTIR, Menu.icone("EMERALD", (peutSertir || peutRetirer ? Menu.OUI : "<white>") + (peutRetirer ? "Retirer une gemme" : "Sertir"), se.toArray(new String[0])),
            peutSertir ? (j, d, s) -> sertir(menu, j) : peutRetirer ? (j, d, s) -> retirerGemme(menu, j) : null);
    }

    private void reparer(Menu menu, Player p) {
        ItemStack objet = menu.getInventory().getItem(OBJET);
        Usure.Reparation r = plugin.usure().reparer(p, objet, 1);
        if (r == Usure.Reparation.FAIT) {
            menu.getInventory().setItem(OBJET, objet);
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.1f);
            p.sendMessage(Textes.message("<#d9c9a8>Objet réparé."));
        }
        boutons(menu, p);
    }

    private void ameliorer(Menu menu, Player p) {
        Inventory inv = menu.getInventory();
        ItemStack objet = inv.getItem(OBJET);
        ItemStack pierre = inv.getItem(COMPOSANT);
        if (ameliorer(p, objet, pierre)) inv.setItem(OBJET, objet);
        inv.setItem(COMPOSANT, pierre == null || pierre.getAmount() <= 0 ? null : pierre);
        boutons(menu, p);
    }

    /**
     * Tente d'améliorer l'objet d'un niveau ; une pierre est consommée dans tous les cas.
     *
     * @return vrai si l'objet a changé
     */
    public boolean ameliorer(Player p, ItemStack objet, ItemStack pierre) {
        Reglages r = plugin.reglages();
        Exemplaire ex = plugin.exemplaires().lire(objet);
        Exemplaire exPierre = plugin.exemplaires().lire(pierre);
        ModeleObjet m = ex == null ? null : plugin.catalogue().modele(ex.modele);
        ModeleObjet mPierre = exPierre == null ? null : plugin.catalogue().modele(exPierre.modele);
        if (m == null || m.empilable() || mPierre == null || !mPierre.type().estPierre() || ex.amelioration >= r.ameliorationMax) return false;
        pierre.setAmount(pierre.getAmount() - 1);
        boolean reussi = ThreadLocalRandom.current().nextDouble() < r.chanceAmelioration(ex.amelioration + 1);
        if (reussi) {
            ex.amelioration++;
            p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
            p.sendMessage(Textes.message("<#7dffb4>Amélioration réussie : <white>+" + ex.amelioration + "<#7dffb4>."));
        } else {
            if (r.ameliorationEchecBaisse && ex.amelioration > 0) ex.amelioration--;
            p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 0.8f);
            p.sendMessage(Textes.message("<#ff5f7e>L'amélioration a échoué." + (r.ameliorationEchecBaisse ? " L'objet retombe à <white>+" + ex.amelioration + "<#ff5f7e>." : "")));
            if (!r.ameliorationEchecBaisse) return false;
        }
        plugin.exemplaires().ecrire(objet, ex);
        return true;
    }

    private void sertir(Menu menu, Player p) {
        Inventory inv = menu.getInventory();
        ItemStack objet = inv.getItem(OBJET);
        ItemStack gemme = inv.getItem(COMPOSANT);
        if (sertir(objet, gemme)) {
            inv.setItem(OBJET, objet);
            inv.setItem(COMPOSANT, gemme.getAmount() <= 0 ? null : gemme);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
            p.sendMessage(Textes.message("<#d9c9a8>Gemme sertie."));
        }
        boutons(menu, p);
    }

    /** Sertit une gemme dans un emplacement libre de l'objet (une gemme est retirée de la pile). Faux si c'est impossible. */
    public boolean sertir(ItemStack objet, ItemStack gemme) {
        Exemplaire ex = plugin.exemplaires().lire(objet);
        Exemplaire exGemme = plugin.exemplaires().lire(gemme);
        ModeleObjet m = ex == null ? null : plugin.catalogue().modele(ex.modele);
        ModeleObjet mGemme = exGemme == null ? null : plugin.catalogue().modele(exGemme.modele);
        if (m == null || m.empilable() || mGemme == null || !mGemme.type().estGemme()) return false;
        if (ex.gemmes.size() >= Calcul.emplacementsGemmes(plugin.catalogue(), m, ex)) return false;
        ex.gemmes.add(mGemme.id());
        gemme.setAmount(gemme.getAmount() - 1);
        plugin.exemplaires().ecrire(objet, ex);
        return true;
    }

    private void retirerGemme(Menu menu, Player p) {
        ItemStack objet = menu.getInventory().getItem(OBJET);
        Exemplaire ex = plugin.exemplaires().lire(objet);
        if (ex == null || ex.gemmes.isEmpty() || !plugin.reglages().gemmeRetirable) return;
        ModeleObjet gemme = plugin.catalogue().modele(ex.gemmes.remove(ex.gemmes.size() - 1));
        plugin.exemplaires().ecrire(objet, ex);
        menu.getInventory().setItem(OBJET, objet);
        if (gemme != null) EcouteurMenus.rendre(p, plugin.exemplaires().creer(gemme, gemme.rang(), false, 0.5, null, Signature.AUCUNE, 1));
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 1f);
        boutons(menu, p);
    }
}

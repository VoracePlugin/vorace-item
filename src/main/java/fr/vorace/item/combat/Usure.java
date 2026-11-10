package fr.vorace.item.combat;

import fr.vorace.item.Reglages;
import fr.vorace.item.Textes;
import fr.vorace.item.VoraceItem;
import fr.vorace.item.api.VoraceItemRepareEvent;
import fr.vorace.item.exemplaire.Exemplaire;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Durabilité des objets. Un objet usé jusqu'au bout n'est pas détruit : il est « cassé », ses stats ne comptent plus,
 * et il se répare. Chaque réparation retire un peu de durabilité maximale pour de bon (0,5 % de la durabilité
 * d'origine) ; sous la moitié de sa durabilité d'origine, l'objet ne se répare plus.
 */
public final class Usure {

    public enum Reparation { FAIT, PAS_UN_OBJET, SANS_DURABILITE, INTACT, TROP_USE }

    private final VoraceItem plugin;

    public Usure(VoraceItem plugin) {
        this.plugin = plugin;
    }

    /**
     * Use l'objet porté à cette place.
     *
     * @param combat vrai si l'usure vient d'un combat (elle compte pour l'apprentissage par la réparation)
     */
    public void user(Player joueur, EquipmentSlot place, int points, boolean combat) {
        if (points <= 0) return;
        ItemStack objet = joueur.getInventory().getItem(place);
        Exemplaire ex = plugin.exemplaires().lire(objet);
        if (ex == null || !ex.aDurabilite() || ex.casse()) return;
        Equipement.Bilan b = plugin.equipement().bilan(joueur);
        ThreadLocalRandom h = ThreadLocalRandom.current();
        if (h.nextDouble() * 100 < b.total("unbreaking-chance")) return;
        double reduit = points * (1 - Math.min(90, Math.max(0, b.total("durability-loss-reduction"))) / 100.0);
        int perte = (int) reduit + (h.nextDouble() < reduit - (int) reduit ? 1 : 0);
        if (perte <= 0) return;
        perte = Math.min(perte, ex.durabilite);
        ex.durabilite -= perte;
        if (combat) ex.usureCombat += perte;
        plugin.exemplaires().ecrire(objet, ex);
        joueur.getInventory().setItem(place, objet);
        if (ex.casse()) {
            plugin.equipement().oublier(joueur);
            joueur.playSound(joueur.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.8f);
            joueur.sendMessage(Textes.message("<#ff5f5f>Ton objet est cassé : <white>" + Textes.echapper(nom(objet)) + "<#ff5f5f>. Fais-le réparer."));
        }
    }

    /** Retire une part de la durabilité restante (mort du joueur : 40 %). Faux si l'objet n'a pas changé. */
    public boolean perdre(ItemStack objet, double part) {
        Exemplaire ex = plugin.exemplaires().lire(objet);
        if (ex == null || !ex.aDurabilite() || ex.casse()) return false;
        int perte = (int) Math.round(ex.durabilite * part);
        if (perte <= 0) return false;
        ex.durabilite = Math.max(0, ex.durabilite - perte);
        plugin.exemplaires().ecrire(objet, ex);
        return true;
    }

    /**
     * Répare l'objet (durabilité remise au maximum, qui baisse un peu pour de bon).
     *
     * @param reparateur     le joueur qui répare (pour l'événement), peut être null
     * @param facteurUsure   multiplicateur de l'usure permanente (1 = normale, 0,5 = passif « Acier éternel »)
     */
    public Reparation reparer(Player reparateur, ItemStack objet, double facteurUsure) {
        Exemplaire ex = plugin.exemplaires().lire(objet);
        if (ex == null) return Reparation.PAS_UN_OBJET;
        if (!ex.aDurabilite()) return Reparation.SANS_DURABILITE;
        if (ex.durabilite >= ex.durabiliteMax) return Reparation.INTACT;
        Reglages r = plugin.reglages();
        int origine = Math.max(ex.durabiliteOrigine, ex.durabiliteMax);
        int plancher = (int) Math.ceil(origine * r.plancherReparation);
        if (ex.durabiliteMax <= plancher) return Reparation.TROP_USE;
        int usure = (int) Math.max(facteurUsure > 0 ? 1 : 0, Math.round(origine * r.usureReparation * Math.max(0, facteurUsure)));
        double partPerdue = 1.0 - (double) ex.durabilite / ex.durabiliteMax;
        int usureCombat = ex.usureCombat;
        ex.durabiliteMax = Math.max(plancher, ex.durabiliteMax - usure);
        ex.durabilite = ex.durabiliteMax;
        ex.usureCombat = 0;
        ex.reparations++;
        plugin.exemplaires().ecrire(objet, ex);
        Bukkit.getPluginManager().callEvent(new VoraceItemRepareEvent(reparateur, objet, ex.modele, partPerdue, usureCombat));
        return Reparation.FAIT;
    }

    private static String nom(ItemStack objet) {
        return objet.hasItemMeta() && objet.getItemMeta().hasDisplayName()
            ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(objet.getItemMeta().displayName())
            : objet.getType().name();
    }
}

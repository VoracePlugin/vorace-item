package fr.vorace.item.forge;

import fr.vorace.item.Textes;
import fr.vorace.item.VoraceItem;
import fr.vorace.item.definitions.Catalogue;
import fr.vorace.item.definitions.Materiau;
import fr.vorace.item.definitions.Materiau.Piece;
import fr.vorace.item.definitions.ModeleObjet;
import fr.vorace.item.exemplaire.Exemplaires.Signature;
import fr.vorace.item.exemplaire.Qualite;
import fr.vorace.item.interfaces.EcouteurMenus;
import fr.vorace.item.interfaces.Menu;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Menus de la forge : la liste de ce qui se fabrique, puis le choix des pièces d'une arme modulaire. */
public final class MenuForge {

    private MenuForge() {}

    /** Liste des objets que la forge sait fabriquer. */
    public static void ouvrir(VoraceItem plugin, Player p, int page) {
        List<ModeleObjet> liste = new ArrayList<>();
        for (ModeleObjet m : plugin.forge().fabricables()) if (!plugin.forge().tropHaut(p, m)) liste.add(m);
        int pages = Math.max(1, (liste.size() + 44) / 45);
        int pg = Math.max(0, Math.min(pages - 1, page));
        Menu menu = new Menu("Forge" + (pages > 1 ? " (" + (pg + 1) + "/" + pages + ")" : ""), 6);
        for (int i = 0; i < 45 && pg * 45 + i < liste.size(); i++) {
            ModeleObjet m = liste.get(pg * 45 + i);
            if (m.kit() != null) {
                Map<String, String> premieres = premieresPieces(plugin.catalogue(), m);
                ItemStack apercu = premieres == null ? null : plugin.exemplaires().apercu(m, plugin.catalogue().raretes().premier(), premieres);
                ItemStack icone = apercu == null ? Menu.icone(m.apparence().materiau(), "<white>" + m.nom())
                    : Menu.icone(apercu, "<white>" + m.nom(), false);
                menu.bouton(i, Menu.icone(icone, null, false, "Arme assemblée pièce par pièce :", "une pièce par emplacement, chacune", "dans le matériau de ton choix.", "",
                    Menu.clic("Clic", "choisir les pièces")), (j, d, s) -> assemblage(plugin, j, m, new LinkedHashMap<>()));
            } else {
                ItemStack apercu = m.empilable() ? plugin.exemplaires().creer(m, m.rang(), false, 0.5, null, Signature.AUCUNE, m.recette().quantite())
                    : plugin.exemplaires().apercu(m, m.rang(), null);
                List<String> lignes = new ArrayList<>(List.of("", "Il te faut :"));
                lignes.addAll(plugin.ingredients().lignes(p, plugin.forge().cout(m, null)));
                lignes.add("");
                lignes.add(Menu.clic("Clic", "fabriquer"));
                menu.bouton(i, Menu.icone(apercu, null, true, lignes.toArray(new String[0])), (j, d, s) -> {
                    donner(plugin, j, plugin.forge().forgerModele(j, m));
                    ouvrir(plugin, j, pg);
                });
            }
        }
        if (liste.isEmpty()) menu.bouton(22, Menu.icone("BARRIER", Menu.NON + "Rien à fabriquer", "Aucun modèle n'a de recette", "(voir plugins/VoraceItem/objets)."), null);
        if (pg > 0) menu.bouton(45, Menu.icone("ARROW", "<white>◀ Page précédente"), (j, d, s) -> ouvrir(plugin, j, pg - 1));
        if (pg < pages - 1) menu.bouton(53, Menu.icone("ARROW", "<white>Page suivante ▶"), (j, d, s) -> ouvrir(plugin, j, pg + 1));
        menu.bouton(49, Menu.icone("BARRIER", Menu.NON + "Fermer"), (j, d, s) -> j.closeInventory());
        menu.ouvrir(p);
    }

    private static Map<String, String> premieresPieces(Catalogue c, ModeleObjet kit) {
        Map<String, String> choix = new LinkedHashMap<>();
        for (String e : kit.kit().emplacements()) {
            List<Piece> pieces = c.pieces(kit.id(), e);
            if (pieces.isEmpty()) return null;
            choix.put(e, pieces.get(0).id());
        }
        return choix;
    }

    /** Choix des pièces d'une arme modulaire : un clic sur une pièce passe à la suivante. */
    public static void assemblage(VoraceItem plugin, Player p, ModeleObjet kit, Map<String, String> choix) {
        Catalogue c = plugin.catalogue();
        Map<String, String> premieres = premieresPieces(c, kit);
        if (premieres == null) {
            p.sendMessage(Textes.message("<red>" + kit.nom() + " : il manque des pièces (voir plugins/VoraceItem/materiaux)."));
            return;
        }
        for (Map.Entry<String, String> e : premieres.entrySet()) {
            Piece actuelle = c.piece(choix.get(e.getKey()));
            if (actuelle == null || !actuelle.kit().equals(kit.id()) || !actuelle.emplacement().equals(e.getKey())) choix.put(e.getKey(), e.getValue());
        }
        Menu menu = new Menu("Forge : " + kit.nom(), 6);
        List<String> emplacements = kit.kit().emplacements();
        int n = Math.min(7, emplacements.size());
        int debut = 13 - (n - 1);
        for (int i = 0; i < n; i++) {
            String emplacement = emplacements.get(i);
            List<Piece> pieces = c.pieces(kit.id(), emplacement);
            Piece piece = c.piece(choix.get(emplacement));
            int index = pieces.indexOf(piece);
            Materiau mat = c.materiau(piece.materiau());
            List<String> lignes = new ArrayList<>();
            lignes.add(Menu.DISCRET + Textes.majuscule(emplacement) + " · " + (index + 1) + " / " + pieces.size());
            double palier = c.raretes().multiplicateurPalier(mat == null ? 1 : mat.palier());
            piece.base().forEach((k, v) -> lignes.add("<#a8a8a8>" + c.modificateurs().nom(k) + " <white>+" + Textes.nombre1(v * palier)));
            piece.modificateurs().forEach((k, v) -> lignes.add("<#8fd18f>" + c.modificateurs().afficher(k, v)));
            if (piece.durabilite() > 0) lignes.add("<#a8a8a8>Durabilité <white>+" + piece.durabilite());
            lignes.add("");
            lignes.addAll(plugin.ingredients().lignes(p, plugin.forge().cout(null0(kit), Map.of(emplacement, piece.id()))));
            lignes.add("");
            lignes.add(Menu.clic("Clic", "pièce suivante"));
            lignes.add(Menu.clic("Clic droit", "pièce précédente"));
            menu.bouton(n <= 4 ? debut + i * 2 : 13 - n / 2 + i, Menu.icone(plugin.forge().iconePiece(kit, piece),
                "<white>" + piece.nom() + " " + (mat == null ? "" : mat.complement()), false, lignes.toArray(new String[0])), (j, droit, maj) -> {
                int suivant = Math.floorMod(index + (droit ? -1 : 1), pieces.size());
                choix.put(emplacement, pieces.get(suivant).id());
                j.playSound(j.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.3f);
                assemblage(plugin, j, kit, choix);
            });
        }
        ItemStack apercu = plugin.exemplaires().apercu(kit, c.raretes().premier(), choix);
        if (apercu != null) menu.bouton(31, Menu.icone(apercu, null, true, "", Menu.DISCRET + "Aperçu : les stats dépendront", Menu.DISCRET + "de la qualité du travail."), null);

        Map<String, Integer> cout = plugin.forge().cout(kit, choix);
        List<String> lignes = new ArrayList<>(List.of("Il te faut :"));
        lignes.addAll(plugin.ingredients().lignes(p, cout));
        boolean possible = plugin.ingredients().possede(p, cout);
        lignes.add("");
        lignes.add(possible ? Menu.clic("Clic", "forger l'arme") : Menu.NON + "Il te manque des matériaux.");
        menu.bouton(49, Menu.icone(possible ? "ANVIL" : "DAMAGED_ANVIL", (possible ? Menu.OUI : Menu.NON) + "<bold>Forger", lignes.toArray(new String[0])), (j, d, s) -> {
            donner(plugin, j, plugin.forge().forgerKit(j, kit, choix));
            assemblage(plugin, j, kit, choix);
        });
        menu.bouton(45, Menu.icone("ARROW", "<white>◀ Retour", "Revenir à la liste."), (j, d, s) -> ouvrir(plugin, j, 0));
        menu.bouton(53, Menu.icone("BARRIER", Menu.NON + "Fermer"), (j, d, s) -> j.closeInventory());
        menu.ouvrir(p);
    }

    /** Pour ne compter que le coût des pièces (pas une éventuelle recette du modèle lui-même). */
    private static ModeleObjet null0(ModeleObjet kit) {
        return kit.recette() == null ? kit : new ModeleObjet(kit.id(), kit.fichier(), kit.nom(), kit.type(), kit.rang(), kit.etage(), kit.apparence(),
            kit.stats(), kit.scales(), kit.nature(), kit.typeAttaque(), kit.element(), kit.vitesseAttaque(), kit.modificateurs(), kit.lignesBonus(),
            kit.durabilite(), kit.niveau(), kit.classes(), kit.competences(), kit.gemmes(), null, kit.description(), kit.kit());
    }

    private static void donner(VoraceItem plugin, Player p, Forge.Resultat r) {
        if (r.refus() != null) {
            p.sendMessage(Textes.message("<#ff5f7e>" + r.refus()));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            return;
        }
        EcouteurMenus.rendre(p, r.objet());
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1f);
        Qualite.Resultat q = r.qualite();
        String nom = r.objet().hasItemMeta() && r.objet().getItemMeta().hasDisplayName()
            ? net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(r.objet().getItemMeta().displayName()) : "l'objet";
        p.sendMessage(Textes.message("<#d9c9a8>Tu as fabriqué " + nom + (q == null ? "<#d9c9a8>." : "<#d9c9a8> : qualité " + q.grade().couleur() + q.grade().nom() + "<#d9c9a8>.")));
    }
}

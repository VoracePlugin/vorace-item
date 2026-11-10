package fr.vorace.item.forge;

import fr.vorace.item.VoraceItem;
import fr.vorace.item.api.VoraceItemCreeEvent;
import fr.vorace.item.definitions.Catalogue;
import fr.vorace.item.definitions.Materiau.Piece;
import fr.vorace.item.definitions.ModeleObjet;
import fr.vorace.item.definitions.Rarete;
import fr.vorace.item.exemplaire.Exemplaires;
import fr.vorace.item.exemplaire.Exemplaires.Signature;
import fr.vorace.item.exemplaire.Qualite;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * La forge de vorace-item : le bouton « Fabriquer » du cahier des métiers de Babel. Le joueur donne les matériaux,
 * l'objet sort avec le résultat d'un débutant (45 % Brute, 50 % Correcte, 5 % Fine), quel que soit son niveau, et
 * jamais au-dessus du rang fixé dans config.yml (Rare). Le vrai travail à la main (gestes, mini-jeux, maîtrise)
 * appartient au plugin des métiers, qui crée ses objets avec {@link fr.vorace.item.api.VoraceItemAPI}.
 */
public final class Forge {

    /** @param refus raison du refus (texte pour le joueur), null si l'objet est fabriqué */
    public record Resultat(ItemStack objet, Qualite.Resultat qualite, String refus) {}

    private final VoraceItem plugin;
    private final Random hasard = new Random();

    public Forge(VoraceItem plugin) {
        this.plugin = plugin;
    }

    /** Modèles que la forge sait fabriquer : armes modulaires et modèles qui ont une recette. */
    public List<ModeleObjet> fabricables() {
        List<ModeleObjet> l = new ArrayList<>();
        for (ModeleObjet m : plugin.catalogue().modeles()) if (m.kit() != null || m.recette() != null) l.add(m);
        return l;
    }

    /** Coût d'un objet : sa recette, ou la somme du coût de ses pièces, multiplié par « forge.surcout ». */
    public Map<String, Integer> cout(ModeleObjet m, Map<String, String> pieces) {
        List<Map<String, Integer>> couts = new ArrayList<>();
        if (m.recette() != null) couts.add(m.recette().materiaux());
        if (m.kit() != null && pieces != null) {
            for (String id : pieces.values()) {
                Piece p = plugin.catalogue().piece(id);
                if (p != null) couts.add(p.cout());
            }
        }
        return Ingredients.somme(couts, plugin.reglages().forgeSurcout);
    }

    /** Vrai si le rang de ce modèle dépasse ce que la forge au bouton sait faire. */
    public boolean tropHaut(Player p, ModeleObjet m) {
        Rarete max = plugin.catalogue().raretes().parId(plugin.reglages().forgeRangMax);
        return max != null && m.rang().ordre() > max.ordre() && !p.hasPermission("voraceitem.forge.tout");
    }

    /** Fabrique un modèle qui a une recette. */
    public Resultat forgerModele(Player p, ModeleObjet m) {
        if (m.recette() == null || m.kit() != null) return new Resultat(null, null, "Cet objet ne se fabrique pas ainsi.");
        if (tropHaut(p, m)) return new Resultat(null, null, "Cet objet est trop complexe pour être fabriqué de cette façon.");
        Map<String, Integer> cout = cout(m, null);
        if (!plugin.ingredients().retirer(p, cout)) return new Resultat(null, null, "Il te manque des matériaux.");
        if (m.empilable()) {
            ItemStack objet = plugin.exemplaires().creer(m, m.rang(), false, 0.5, null, Signature.AUCUNE, m.recette().quantite());
            return new Resultat(annoncer(p, m, objet), null, null);
        }
        Qualite.Resultat q = plugin.qualite().calculer(plugin.qualite().scoreBouton(hasard), m.rang(), 0, 0, hasard);
        ItemStack objet = plugin.exemplaires().creer(m, q.rang(), q.rebut(), q.position(), null,
            new Signature(p.getName(), p.getUniqueId(), q.grade().id(), q.score(), null), 1);
        return new Resultat(annoncer(p, m, objet), q, null);
    }

    /** Assemble une arme modulaire avec les pièces choisies (emplacement → identifiant de pièce). */
    public Resultat forgerKit(Player p, ModeleObjet kit, Map<String, String> pieces) {
        if (kit.kit() == null) return new Resultat(null, null, "Ce modèle n'est pas une arme à assembler.");
        Catalogue c = plugin.catalogue();
        for (String e : kit.kit().emplacements()) {
            Piece piece = c.piece(pieces.get(e));
            if (piece == null || !piece.kit().equals(kit.id()) || !piece.emplacement().equals(e)) return new Resultat(null, null, "Il manque une pièce : " + e + ".");
        }
        Map<String, Integer> cout = cout(kit, pieces);
        if (!plugin.ingredients().retirer(p, cout)) return new Resultat(null, null, "Il te manque des matériaux.");
        Qualite.Resultat q = plugin.qualite().calculer(plugin.qualite().scoreBouton(hasard), c.raretes().premier(), 0, 0, hasard);
        ItemStack objet = plugin.exemplaires().creer(kit, q.rang(), q.rebut(), q.position(), pieces,
            new Signature(p.getName(), p.getUniqueId(), q.grade().id(), q.score(), null), 1);
        if (objet == null) return new Resultat(null, null, "Ces pièces ne vont pas ensemble.");
        return new Resultat(annoncer(p, kit, objet), q, null);
    }

    private ItemStack annoncer(Player p, ModeleObjet m, ItemStack objet) {
        VoraceItemCreeEvent e = new VoraceItemCreeEvent(p, m.id(), objet, VoraceItemCreeEvent.Origine.FORGE);
        Bukkit.getPluginManager().callEvent(e);
        return e.getObjet();
    }

    /** Objet qui montre une seule pièce d'un kit (les autres emplacements restent vides). */
    public ItemStack iconePiece(ModeleObjet kit, Piece piece) {
        ItemStack it = new ItemStack(fr.vorace.item.interfaces.Menu.materiau(kit.apparence().materiau()));
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.setItemModel(Exemplaires.cle("kit/" + kit.id()));
        List<String> chaines = new ArrayList<>();
        for (String e : kit.kit().emplacements()) chaines.add(e.equals(piece.emplacement()) ? piece.id() : "");
        chaines.add("");
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(chaines);
        meta.setCustomModelDataComponent(cmd);
        it.setItemMeta(meta);
        return it;
    }
}

package fr.vorace.item.exemplaire;

import fr.vorace.item.Textes;
import fr.vorace.item.VoraceItem;
import fr.vorace.item.definitions.Catalogue;
import fr.vorace.item.definitions.Materiau;
import fr.vorace.item.definitions.Materiau.Piece;
import fr.vorace.item.definitions.ModeleObjet;
import fr.vorace.item.definitions.Rarete;
import fr.vorace.item.exemplaire.Exemplaires.Signature;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Les pièces d'armes modulaires en tant qu'objets : une lame, une garde, une poignée ou un pommeau que l'on tient
 * dans son inventaire. Une pièce ne porte que son nom, et ses stats quand elle en a (dans les exemples, seule la
 * lame en a). Elle n'a pas de qualité : la qualité de l'arme se décide à la fin, à l'assemblage.
 * <p>
 * vorace-item ne décide pas comment une pièce se fabrique ni comment le score final s'obtient (gestes, mini-jeux,
 * niveaux de métier) : le plugin des métiers appelle {@link #creer} quand la pièce est faite et {@link #assembler},
 * avec le score, quand le joueur monte son arme.
 */
public final class Pieces {

    /**
     * Résultat d'un assemblage.
     *
     * @param objet   l'arme, ou null si l'assemblage est refusé
     * @param qualite grade, rang et place dans la fourchette obtenus (null si refusé)
     * @param erreur  raison du refus, à montrer au joueur (null si l'arme est faite)
     */
    public record Assemblage(ItemStack objet, Qualite.Resultat qualite, String erreur) {}

    private final VoraceItem plugin;
    private final NamespacedKey kPiece = Exemplaires.cle("piece");

    public Pieces(VoraceItem plugin) {
        this.plugin = plugin;
    }

    /** Crée l'objet d'une pièce. Deux pièces identiques s'empilent. */
    public ItemStack creer(Piece piece) {
        if (piece == null) return null;
        Catalogue c = plugin.catalogue();
        ItemStack objet = new ItemStack(Material.PAPER);
        ItemMeta meta = objet.getItemMeta();
        if (meta == null) return null;
        Materiau materiau = c.materiau(piece.materiau());
        meta.displayName(Textes.objet("<white>" + Textes.echapper(piece.nom() + (materiau == null ? "" : " " + materiau.complement()))));
        List<Component> lignes = new ArrayList<>();
        double palier = materiau == null ? 1 : c.raretes().multiplicateurPalier(materiau.palier());
        for (Map.Entry<String, Double> e : piece.base().entrySet()) {
            lignes.add(Textes.objet("<#d9c9a8>" + Textes.echapper(c.modificateurs().afficher(e.getKey(), e.getValue() * palier))));
        }
        for (Map.Entry<String, Double> e : piece.modificateurs().entrySet()) {
            lignes.add(Textes.objet("<#7db8ff>" + Textes.echapper(c.modificateurs().afficher(e.getKey(), e.getValue()))));
        }
        if (!lignes.isEmpty()) meta.lore(lignes);
        meta.setItemModel(Exemplaires.cle("piece/" + piece.id()));
        meta.getPersistentDataContainer().set(kPiece, PersistentDataType.STRING, piece.id());
        objet.setItemMeta(meta);
        return objet;
    }

    /** Identifiant de la pièce que porte cet objet (« fer/lame_droite »), ou null si ce n'est pas une pièce. */
    public String lire(ItemStack objet) {
        if (objet == null || objet.getType().isAir() || !objet.hasItemMeta()) return null;
        ItemMeta meta = objet.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(kPiece, PersistentDataType.STRING);
    }

    /**
     * Assemble une arme à partir de ses pièces. Il faut exactement une pièce par emplacement du kit. La qualité se
     * décide ici : le score donne le grade de l'arme, son rang et sa place dans la fourchette (règles de
     * {@link Qualite#calculer}). Les objets des pièces ne sont pas retirés : c'est à l'appelant d'en retirer un de
     * chaque quand l'assemblage a réussi.
     *
     * @param score         score final de fabrication, de 0 à 100
     * @param rangMateriau  rang du matériau principal (null : le premier rang)
     * @param chanceRangSup chance (0 à 1) qu'un Chef-d'œuvre gagne un rang
     * @param ecart         étages entre la recette et le bas du rang de maîtrise du fabricant
     * @param fabricant     nom de celui qui assemble (null : pas de fabricant sur l'arme)
     */
    public Assemblage assembler(ModeleObjet kit, Collection<ItemStack> objets, double score, Rarete rangMateriau, double chanceRangSup,
                                double ecart, String fabricant, UUID fabricantUuid, Random hasard) {
        if (kit == null || kit.kit() == null) return new Assemblage(null, null, "Ce modèle n'est pas une arme à assembler.");
        Catalogue c = plugin.catalogue();
        Map<String, String> pieces = new LinkedHashMap<>();
        for (ItemStack objet : objets) {
            Piece piece = c.piece(lire(objet));
            if (piece == null) return new Assemblage(null, null, "Un de ces objets n'est pas une pièce d'arme.");
            if (!piece.kit().equals(kit.id())) return new Assemblage(null, null, piece.nom() + " ne va pas sur : " + kit.nom() + ".");
            if (pieces.put(piece.emplacement(), piece.id()) != null) return new Assemblage(null, null, "Il y a deux pièces pour : " + piece.emplacement() + ".");
        }
        for (String e : kit.kit().emplacements()) if (!pieces.containsKey(e)) return new Assemblage(null, null, "Il manque une pièce : " + e + ".");
        // les pièces dans l'ordre des emplacements du kit
        Map<String, String> rangees = new LinkedHashMap<>();
        for (String e : kit.kit().emplacements()) rangees.put(e, pieces.get(e));
        Qualite.Resultat q = plugin.qualite().calculer(score, rangMateriau == null ? c.raretes().premier() : rangMateriau,
            chanceRangSup, ecart, hasard == null ? new Random() : hasard);
        ItemStack arme = plugin.exemplaires().creer(kit, q.rang(), q.rebut(), q.position(), rangees,
            new Signature(fabricant, fabricantUuid, q.grade().id(), q.score(), null), 1);
        if (arme == null) return new Assemblage(null, null, "Ces pièces ne vont pas ensemble.");
        return new Assemblage(arme, q, null);
    }
}

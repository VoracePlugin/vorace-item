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
import org.bukkit.persistence.PersistentDataContainer;
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
 * dans son inventaire. Rien n'est écrit sur une pièce, à part son nom : ni stats, ni qualité. Sa qualité existe
 * pourtant (un score de 0 à 100, gardé caché dans l'objet) : le joueur ne la découvre qu'en finissant l'arme, qui
 * prend la moyenne des scores de ses pièces.
 * <p>
 * vorace-item ne décide pas comment une pièce se fabrique ni quel score elle mérite (gestes, mini-jeux, niveaux de
 * métier) : le plugin des métiers appelle {@link #creer} avec le score quand la pièce est faite, et
 * {@link #assembler} quand le joueur monte son arme.
 */
public final class Pieces {

    /** Ce que porte une pièce tenue en objet : son identifiant et son score caché. */
    public record PieceObjet(String id, double score) {}

    /**
     * Résultat d'un assemblage.
     *
     * @param objet   l'arme, ou null si l'assemblage est refusé
     * @param qualite grade, rang et place dans la fourchette obtenus (null si refusé)
     * @param erreur  raison du refus, à montrer au joueur (null si l'arme est faite)
     */
    public record Assemblage(ItemStack objet, Qualite.Resultat qualite, String erreur) {}

    private final VoraceItem plugin;
    private final NamespacedKey kPiece = Exemplaires.cle("piece"), kScore = Exemplaires.cle("piece_score");

    public Pieces(VoraceItem plugin) {
        this.plugin = plugin;
    }

    /**
     * Crée l'objet d'une pièce : son nom, son apparence, et rien d'autre de visible.
     *
     * @param score score de fabrication de la pièce, de 0 à 100 (caché : il ne compte qu'à l'assemblage)
     */
    public ItemStack creer(Piece piece, double score) {
        if (piece == null) return null;
        ItemStack objet = new ItemStack(Material.PAPER);
        ItemMeta meta = objet.getItemMeta();
        if (meta == null) return null;
        Materiau materiau = plugin.catalogue().materiau(piece.materiau());
        meta.displayName(Textes.objet("<white>" + Textes.echapper(piece.nom() + (materiau == null ? "" : " " + materiau.complement()))));
        meta.setItemModel(Exemplaires.cle("piece/" + piece.id()));
        // une seule par case : deux pièces qui s'empileraient trahiraient qu'elles ont le même score
        meta.setMaxStackSize(1);
        PersistentDataContainer d = meta.getPersistentDataContainer();
        d.set(kPiece, PersistentDataType.STRING, piece.id());
        d.set(kScore, PersistentDataType.DOUBLE, Math.max(0, Math.min(100, score)));
        objet.setItemMeta(meta);
        return objet;
    }

    /** Ce que porte cet objet s'il est une pièce, sinon null. */
    public PieceObjet lire(ItemStack objet) {
        if (objet == null || objet.getType().isAir() || !objet.hasItemMeta()) return null;
        ItemMeta meta = objet.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer d = meta.getPersistentDataContainer();
        String id = d.get(kPiece, PersistentDataType.STRING);
        return id == null ? null : new PieceObjet(id, d.getOrDefault(kScore, PersistentDataType.DOUBLE, 0.0));
    }

    /**
     * Assemble une arme à partir de ses pièces. Il faut exactement une pièce par emplacement du kit. Le score de
     * l'arme est la moyenne des scores des pièces ; il donne son grade, son rang et sa place dans la fourchette
     * (mêmes règles que {@link Qualite#calculer}). Les objets des pièces ne sont pas retirés : c'est à l'appelant de
     * le faire quand l'assemblage a réussi.
     *
     * @param rangMateriau  rang du matériau principal (null : le premier rang)
     * @param chanceRangSup chance (0 à 1) qu'un Chef-d'œuvre gagne un rang
     * @param ecart         étages entre la recette et le bas du rang de maîtrise du fabricant
     * @param fabricant     nom de celui qui assemble (null : pas de fabricant sur l'arme)
     */
    public Assemblage assembler(ModeleObjet kit, Collection<ItemStack> objets, Rarete rangMateriau, double chanceRangSup,
                                double ecart, String fabricant, UUID fabricantUuid, Random hasard) {
        if (kit == null || kit.kit() == null) return new Assemblage(null, null, "Ce modèle n'est pas une arme à assembler.");
        Catalogue c = plugin.catalogue();
        Map<String, String> pieces = new LinkedHashMap<>();
        double somme = 0;
        for (ItemStack objet : objets) {
            PieceObjet lue = lire(objet);
            Piece piece = lue == null ? null : c.piece(lue.id());
            if (piece == null) return new Assemblage(null, null, "Un de ces objets n'est pas une pièce d'arme.");
            if (!piece.kit().equals(kit.id())) return new Assemblage(null, null, piece.nom() + " ne va pas sur : " + kit.nom() + ".");
            if (pieces.put(piece.emplacement(), piece.id()) != null) return new Assemblage(null, null, "Il y a deux pièces pour : " + piece.emplacement() + ".");
            somme += lue.score();
        }
        for (String e : kit.kit().emplacements()) if (!pieces.containsKey(e)) return new Assemblage(null, null, "Il manque une pièce : " + e + ".");
        double score = somme / pieces.size();
        Qualite.Resultat q = plugin.qualite().calculer(score, rangMateriau == null ? c.raretes().premier() : rangMateriau,
            chanceRangSup, ecart, hasard == null ? new Random() : hasard);
        ItemStack arme = plugin.exemplaires().creer(kit, q.rang(), q.rebut(), q.position(), pieces,
            new Signature(fabricant, fabricantUuid, q.grade().id(), q.score(), null), 1);
        if (arme == null) return new Assemblage(null, null, "Ces pièces ne vont pas ensemble.");
        return new Assemblage(arme, q, null);
    }
}

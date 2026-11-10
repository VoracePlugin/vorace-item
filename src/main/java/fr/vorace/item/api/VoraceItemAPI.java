package fr.vorace.item.api;

import fr.vorace.item.VoraceItem;
import fr.vorace.item.combat.Usure;
import fr.vorace.item.competences.Competences;
import fr.vorace.item.definitions.Catalogue;
import fr.vorace.item.definitions.Materiau.Piece;
import fr.vorace.item.definitions.ModeleObjet;
import fr.vorace.item.definitions.Rarete;
import fr.vorace.item.exemplaire.Calcul;
import fr.vorace.item.exemplaire.Exemplaire;
import fr.vorace.item.exemplaire.Exemplaires.Signature;
import fr.vorace.item.exemplaire.Pieces;
import fr.vorace.item.exemplaire.Qualite;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Accès à vorace-item depuis un autre plugin (métiers, classes, quêtes, monstres).
 * <pre>
 * // un objet fabriqué par un joueur : le score décide du grade, du rang et de la place dans la fourchette de stats
 * Qualite.Resultat q = VoraceItemAPI.qualite(82, "rare", 0, 0, new Random());
 * ItemStack epee = VoraceItemAPI.creerExemplaire("epee_longue", q.rang().id(), q.position(), null,
 *     new Signature(joueur.getName(), joueur.getUniqueId(), q.grade().id(), q.score(), null));
 *
 * // une arme assemblée pièce par pièce
 * ItemStack arme = VoraceItemAPI.assembler("epee", Map.of("lame", "fer/lame_droite", "garde", "fer/garde_droite",
 *     "poignee", "fer/poignee_cuir", "pommeau", "fer/pommeau_rond"), "commun", 0.4, null, Signature.AUCUNE);
 *
 * double attaque = VoraceItemAPI.stats(epee).getOrDefault("attaque", 0.0);
 * </pre>
 * Toutes les méthodes renvoient null, 0, faux ou une liste vide si vorace-item n'est pas chargé.
 */
public final class VoraceItemAPI {

    private static VoraceItem plugin;

    private VoraceItemAPI() {}

    public static void initialiser(VoraceItem p) { plugin = p; }

    // ------------------------------------------------------------------ création

    /**
     * Crée un exemplaire d'un modèle.
     *
     * @param modele       identifiant du modèle (objets/*.yml)
     * @param rarete       rang de l'exemplaire (« rare », « Peu commune »...) ; null = rang de base du modèle
     * @param position     place dans la fourchette de stats du rang, de 0 (bas) à 1 (haut)
     * @param exemplaireId identifiant unique de l'objet ; null = tiré au hasard
     * @param signature    fabricant, grade et score ; null ou {@link Signature#AUCUNE} pour un objet sans fabricant
     * @return l'objet, ou null si le modèle ou le rang n'existe pas (ou si c'est une arme modulaire : voir {@link #assembler})
     */
    public static ItemStack creerExemplaire(String modele, String rarete, double position, UUID exemplaireId, Signature signature) {
        return creer(modele, rarete, false, position, null, exemplaireId, signature);
    }

    /** Comme {@link #creerExemplaire}, pour un objet Brut de matériau Commun : ses stats sont dans la fourchette « rebut ». */
    public static ItemStack creerRebut(String modele, double position, UUID exemplaireId, Signature signature) {
        if (plugin == null) return null;
        return creer(modele, plugin.catalogue().raretes().premier().id(), true, position, null, exemplaireId, signature);
    }

    /**
     * Assemble une arme modulaire.
     *
     * @param kit    identifiant du modèle modulaire (« epee »)
     * @param pieces emplacement → identifiant de pièce (« lame » → « fer/lame_droite ») : une pièce par emplacement du kit
     * @return l'arme, ou null si une pièce manque, n'existe pas ou n'appartient pas à ce kit
     */
    public static ItemStack assembler(String kit, Map<String, String> pieces, String rarete, double position, UUID exemplaireId, Signature signature) {
        return creer(kit, rarete, false, position, pieces, exemplaireId, signature);
    }

    /**
     * Crée l'objet d'une pièce d'arme (une lame, une garde...), que le joueur garde dans son inventaire jusqu'à
     * l'assemblage. À appeler quand le joueur a fabriqué la pièce.
     *
     * @param piece identifiant de la pièce (« fer/lame_droite »)
     * @param score score de fabrication de la pièce, de 0 à 100 (il donne son grade)
     * @return l'objet, ou null si la pièce n'existe pas
     */
    public static ItemStack creerPiece(String piece, double score, String fabricant, UUID fabricantUuid) {
        return plugin == null ? null : plugin.pieces().creer(plugin.catalogue().piece(piece), score, fabricant, fabricantUuid);
    }

    /** Ce que porte cet objet s'il est une pièce d'arme (identifiant, score, fabricant), sinon null. */
    public static Pieces.PieceObjet piece(ItemStack objet) {
        return plugin == null ? null : plugin.pieces().lire(objet);
    }

    /** Ce que coûte la fabrication d'une pièce (« iron_ingot » → 2), d'après materiaux/*.yml. Vide si elle n'existe pas. */
    public static Map<String, Integer> coutPiece(String piece) {
        Piece p = plugin == null ? null : plugin.catalogue().piece(piece);
        return p == null ? Map.of() : Map.copyOf(p.cout());
    }

    /**
     * Assemble une arme à partir des objets de ses pièces : exactement une pièce par emplacement du kit. Le score de
     * l'arme est la moyenne des scores des pièces. Les objets des pièces ne sont pas retirés : retire-les quand
     * {@code objet()} n'est pas null.
     *
     * @param kit           identifiant du modèle modulaire (« epee »)
     * @param rangMateriau  rang du matériau principal (null : le premier rang)
     * @param chanceRangSup chance (0 à 1) qu'un Chef-d'œuvre gagne un rang
     * @param ecart         étages entre la recette et le bas du rang de maîtrise du fabricant
     * @param fabricant     celui qui assemble (son nom est gravé sur l'arme), ou null
     * @return l'arme et sa qualité, ou la raison du refus dans {@code erreur()}
     */
    public static Pieces.Assemblage assemblerPieces(String kit, Collection<ItemStack> pieces, String rangMateriau,
                                                    double chanceRangSup, double ecart, Player fabricant, Random hasard) {
        if (plugin == null) return new Pieces.Assemblage(null, null, "vorace-item n'est pas chargé.");
        Rarete rang = rangMateriau == null ? null : plugin.catalogue().raretes().parId(rangMateriau);
        return plugin.pieces().assembler(plugin.catalogue().modele(kit), pieces, rang, chanceRangSup, ecart,
            fabricant == null ? null : fabricant.getName(), fabricant == null ? null : fabricant.getUniqueId(), hasard);
    }

    private static ItemStack creer(String modele, String rarete, boolean rebut, double position, Map<String, String> pieces, UUID id, Signature s) {
        if (plugin == null) return null;
        ModeleObjet m = plugin.catalogue().modele(modele);
        if (m == null) return null;
        Rarete rang = rarete == null ? m.rang() : plugin.catalogue().raretes().parId(rarete);
        if (rang == null) return null;
        Signature sig = s == null ? Signature.AUCUNE : s;
        if (id != null) sig = new Signature(sig.fabricant(), sig.fabricantUuid(), sig.grade(), sig.score(), id);
        return plugin.exemplaires().creer(m, rang, rebut, position, pieces, sig, 1);
    }

    /**
     * Du score de fabrication au résultat (règles du cahier des métiers de Babel, partie 4.4).
     *
     * @param score         score final, de 0 à 100
     * @param rangMateriau  rang du matériau principal de la recette
     * @param chanceRangSup chance (0 à 1) qu'un Chef-d'œuvre gagne un rang
     * @param ecart         étages entre la recette et le bas du rang de maîtrise du fabricant
     */
    public static Qualite.Resultat qualite(double score, String rangMateriau, double chanceRangSup, double ecart, Random hasard) {
        if (plugin == null) return null;
        Rarete rang = plugin.catalogue().raretes().parId(rangMateriau);
        return rang == null ? null : plugin.qualite().calculer(score, rang, chanceRangSup, ecart, hasard);
    }

    // ------------------------------------------------------------------ lecture

    /** Identifiant du modèle de cet objet, ou null si ce n'est pas un objet de vorace-item. */
    public static String modele(ItemStack objet) {
        return plugin == null ? null : plugin.exemplaires().modele(objet);
    }

    /** Ce qui appartient à cet exemplaire (rang, qualité, fabricant, pièces, gemmes, usure), ou null. */
    public static Exemplaire exemplaire(ItemStack objet) {
        return plugin == null ? null : plugin.exemplaires().lire(objet);
    }

    /** Stats de cet objet : « attaque », « puissance », « armor », « magic-armor » et les modificateurs. */
    public static Map<String, Double> stats(ItemStack objet) {
        return plugin == null ? Map.of() : plugin.exemplaires().stats(objet);
    }

    /** Total d'une stat sur l'équipement que porte le joueur (objets à leur place, non cassés, conditions remplies). */
    public static double total(Player joueur, String stat) {
        return plugin == null ? 0 : plugin.equipement().bilan(joueur).total(plugin.catalogue().modificateurs().id(stat));
    }

    /**
     * Fourchette {minimum, maximum} d'une stat d'un modèle à un rang.
     *
     * @param pieces arme modulaire : emplacement → pièce ; null sinon
     * @param rarete rang, ou « rebut » pour la fourchette sous le premier rang
     */
    public static double[] fourchette(String modele, Map<String, String> pieces, String stat, String rarete) {
        if (plugin == null) return null;
        Catalogue c = plugin.catalogue();
        ModeleObjet m = c.modele(modele);
        if (m == null) return null;
        boolean rebut = "rebut".equalsIgnoreCase(rarete);
        Rarete rang = rebut ? c.raretes().premier() : c.raretes().parId(rarete);
        if (rang == null) return null;
        Exemplaire ex = new Exemplaire();
        ex.modele = m.id();
        if (pieces != null) ex.pieces.putAll(pieces);
        return Calcul.fourchette(c, m, ex, stat, rang, rebut);
    }

    /** Identifiants de tous les modèles. */
    public static List<String> modeles() {
        if (plugin == null) return List.of();
        List<String> l = new ArrayList<>();
        for (ModeleObjet m : plugin.catalogue().modeles()) l.add(m.id());
        return l;
    }

    /** Identifiants des pièces d'un kit pour un emplacement (« epee », « lame »). */
    public static List<String> pieces(String kit, String emplacement) {
        if (plugin == null) return List.of();
        List<String> l = new ArrayList<>();
        for (Piece p : plugin.catalogue().pieces(kit, emplacement)) l.add(p.id());
        return l;
    }

    /** Vrai si ces pièces forment une arme complète de ce kit (une pièce valable par emplacement). */
    public static boolean compatibles(String kit, Map<String, String> pieces) {
        if (plugin == null || pieces == null) return false;
        ModeleObjet m = plugin.catalogue().modele(kit);
        if (m == null || m.kit() == null) return false;
        for (String e : m.kit().emplacements()) {
            Piece p = plugin.catalogue().piece(pieces.get(e));
            if (p == null || !p.kit().equals(m.id()) || !p.emplacement().equals(e)) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ modification

    /** Réécrit l'objet après un changement de ses données (obtenues avec {@link #exemplaire}). Faux si le modèle n'existe plus. */
    public static boolean enregistrer(ItemStack objet, Exemplaire exemplaire) {
        return plugin != null && plugin.exemplaires().ecrire(objet, exemplaire);
    }

    /**
     * Ajoute une ligne bonus de plus à l'objet, tirée parmi celles que permet son modèle (passifs de métier).
     *
     * @return l'identifiant du modificateur ajouté, ou null si l'objet ne peut plus en recevoir
     */
    public static String ajouterLigneBonus(ItemStack objet) {
        return plugin == null ? null : plugin.exemplaires().ajouterBonus(objet);
    }

    /**
     * Répare l'objet (voir {@link VoraceItemRepareEvent}).
     *
     * @param facteurUsure multiplicateur de l'usure permanente de la réparation (1 = normale, 0,5 = moitié moins)
     */
    public static Usure.Reparation reparer(Player reparateur, ItemStack objet, double facteurUsure) {
        return plugin == null ? Usure.Reparation.PAS_UN_OBJET : plugin.usure().reparer(reparateur, objet, facteurUsure);
    }

    // ------------------------------------------------------------------ combat et compétences

    /**
     * Fait porter par le joueur un coup calculé avec la formule de Babel (Puissance² ÷ (Puissance + Défense)).
     *
     * @param multiplicateur multiplicateur de compétence (1 = coup normal)
     * @param magique        true ou false pour imposer la nature du coup, null pour celle de l'arme tenue
     * @param competence     identifiant donné à {@link VoraceItemDegatsEvent#getCompetence()} (peut être null)
     * @return les dégâts infligés (0 si esquivé ou annulé)
     */
    public static double frapper(Player attaquant, LivingEntity cible, double multiplicateur, Boolean magique, String competence) {
        return plugin == null ? 0 : plugin.combat().frapper(attaquant, cible, multiplicateur, magique, competence);
    }

    /** Fournit la défense des monstres (Résistance + Armure). null = revenir au calcul par défaut. */
    public static void defenseMonstres(DefenseMonstre defense) {
        if (plugin != null) plugin.combat().defenseMonstres(defense);
    }

    /**
     * Lance une compétence d'arme (competences.yml).
     *
     * @param verifier vrai pour respecter la recharge et dépenser mana et stamina
     */
    public static Competences.Lancer lancerCompetence(Player joueur, String competence, boolean verifier) {
        return plugin == null ? Competences.Lancer.INCONNUE : plugin.competences().lancer(joueur, competence, null, verifier);
    }

    /** Tire le butin d'une table de butins.yml, comme si un monstre venait de mourir (la chance de la table s'applique). */
    public static List<ItemStack> tirerButin(String table, Player joueur) {
        if (plugin == null || table == null) return List.of();
        return plugin.butins().tirer(plugin.butins().tables().get(table.toLowerCase(java.util.Locale.ROOT)), joueur,
            joueur == null ? 0 : plugin.equipement().bilan(joueur).total("luck"), false);
    }
}

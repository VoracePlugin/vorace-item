package fr.vorace.item.exemplaire;

import fr.vorace.item.definitions.Catalogue;
import fr.vorace.item.definitions.Materiau;
import fr.vorace.item.definitions.Materiau.Piece;
import fr.vorace.item.definitions.ModeleObjet;
import fr.vorace.item.definitions.Rarete;

import java.util.LinkedHashMap;
import java.util.Map;

/** Calcul des stats d'un exemplaire à partir de son modèle, de ses pièces, de son rang et de sa place dans la fourchette. */
public final class Calcul {

    private Calcul() {}

    /**
     * Valeurs de référence au rang Commun des stats qui suivent le rang : celles du modèle, plus, pour une arme
     * modulaire, la base de chaque pièce multipliée par le palier de son matériau.
     */
    public static Map<String, Double> references(Catalogue c, ModeleObjet m, Exemplaire ex) {
        Map<String, Double> ref = new LinkedHashMap<>(m.stats());
        for (String idPiece : ex.pieces.values()) {
            Piece p = c.piece(idPiece);
            if (p == null) continue;
            Materiau mat = c.materiau(p.materiau());
            double palier = c.raretes().multiplicateurPalier(mat == null ? 1 : mat.palier());
            for (Map.Entry<String, Double> e : p.base().entrySet()) ref.merge(e.getKey(), e.getValue() * palier, Double::sum);
        }
        return ref;
    }

    /** Fourchette {minimum, maximum} d'une stat de ce modèle à ce rang (sans amélioration, sans bonus). */
    public static double[] fourchette(Catalogue c, ModeleObjet m, Exemplaire ex, String stat, Rarete rang, boolean rebut) {
        Double ref = references(c, m, ex).get(c.modificateurs().id(stat));
        return ref == null ? null : c.raretes().fourchette(ref, rang, rebut);
    }

    /**
     * Toutes les stats de l'exemplaire : stats de rang (à leur place dans la fourchette, avec l'amélioration),
     * modificateurs fixes du modèle et des pièces, lignes bonus, gemmes.
     *
     * @param bonusAmelioration part ajoutée aux stats de rang par niveau d'amélioration (0,03 = +3 % par niveau)
     */
    public static Map<String, Double> stats(Catalogue c, Exemplaire ex, double bonusAmelioration) {
        Map<String, Double> total = new LinkedHashMap<>();
        ModeleObjet m = c.modele(ex.modele);
        if (m == null) return total;
        Rarete rang = c.raretes().parId(ex.rang);
        if (rang == null) rang = m.rang();
        double amelioration = 1 + Math.max(0, ex.amelioration) * bonusAmelioration;
        for (Map.Entry<String, Double> e : references(c, m, ex).entrySet()) {
            total.merge(e.getKey(), c.raretes().valeur(e.getValue(), rang, ex.rebut, ex.position) * amelioration, Double::sum);
        }
        m.modificateurs().forEach((k, v) -> total.merge(k, v, Double::sum));
        for (String idPiece : ex.pieces.values()) {
            Piece p = c.piece(idPiece);
            if (p != null) p.modificateurs().forEach((k, v) -> total.merge(k, v, Double::sum));
        }
        ex.bonus.forEach((k, v) -> total.merge(k, v, Double::sum));
        for (String g : ex.gemmes) {
            ModeleObjet gemme = c.modele(g);
            if (gemme != null) gemme.modificateurs().forEach((k, v) -> total.merge(k, v, Double::sum));
        }
        return total;
    }

    /** Durabilité d'origine : celle du modèle, plus celle de chaque pièce. 0 = l'objet ne s'use pas. */
    public static int durabiliteOrigine(Catalogue c, ModeleObjet m, Exemplaire ex) {
        int d = m.durabilite();
        for (String idPiece : ex.pieces.values()) {
            Piece p = c.piece(idPiece);
            if (p != null) d += p.durabilite();
        }
        return d;
    }

    /** Nombre d'emplacements de gemmes : celui du modèle, sinon celui du rang. */
    public static int emplacementsGemmes(Catalogue c, ModeleObjet m, Exemplaire ex) {
        if (m.type().empilable()) return 0;
        if (m.gemmes() >= 0) return m.gemmes();
        Rarete rang = c.raretes().parId(ex.rang);
        return rang == null ? 0 : rang.gemmes();
    }

    /** Palier qui fixe la force des lignes bonus : celui du matériau de la première pièce, sinon celui du modèle. */
    public static int palier(Catalogue c, ModeleObjet m, Exemplaire ex) {
        for (String idPiece : ex.pieces.values()) {
            Piece p = c.piece(idPiece);
            Materiau mat = p == null ? null : c.materiau(p.materiau());
            if (mat != null) return mat.palier();
        }
        return m.palier();
    }
}

package fr.vorace.item.exemplaire;

import fr.vorace.item.Textes;
import fr.vorace.item.definitions.Rarete;
import fr.vorace.item.definitions.Raretes;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Random;

/**
 * Du score de fabrication (0 à 100) au grade, au rang et à la place dans la fourchette de stats.
 * Règles du cahier des métiers de Babel (parties 4.2 et 4.4) :
 * <ul>
 *   <li>Brute sous 30, Correcte de 30 à 54, Fine de 55 à 69, Excellente de 70 à 84, Chef-d'œuvre à partir de 85 ;</li>
 *   <li>sous le score exigé par le rang du matériau (30 jusqu'à l'Épique, 70 en Légendaire et Mythique, 85 en Divin),
 *       l'objet sort au rang inférieur, tout en haut de sa fourchette quand il frôle le seuil ;</li>
 *   <li>un Chef-d'œuvre peut gagner un rang (chance donnée par l'appelant) : il arrive alors en bas de la fourchette du dessus ;</li>
 *   <li>sinon la place dans la fourchette suit le score, avec un petit aléa rabattu aux bords : aucune marche entre deux grades.</li>
 * </ul>
 */
public final class Qualite {

    public enum Grade {
        BRUTE("brute", "Brute", "<#9a8f85>"),
        CORRECTE("correcte", "Correcte", "<#d8d8d8>"),
        FINE("fine", "Fine", "<#8fd18f>"),
        EXCELLENTE("excellente", "Excellente", "<#7db8ff>"),
        CHEF_D_OEUVRE("chef_d_oeuvre", "Chef-d'œuvre", "<#ffd166>");

        private final String id;
        private final String nom;
        private final String couleur;

        Grade(String id, String nom, String couleur) {
            this.id = id;
            this.nom = nom;
            this.couleur = couleur;
        }

        public String id() { return id; }
        public String nom() { return nom; }
        public String couleur() { return couleur; }

        public static Grade parId(String id) {
            if (id == null) return null;
            String c = Textes.cle(id).replace('-', '_');
            for (Grade g : values()) if (g.id.equals(c) || Textes.cle(g.nom).replace('-', '_').equals(c)) return g;
            return null;
        }
    }

    /**
     * Résultat d'une fabrication.
     *
     * @param rebut    vrai si l'objet est sous le premier rang (Brute de matériau Commun)
     * @param position place dans la fourchette de stats, de 0 à 1
     */
    public record Resultat(Grade grade, Rarete rang, boolean rebut, double position, double score) {}

    private final Raretes raretes;
    private final double[] seuils;   // Correcte, Fine, Excellente, Chef-d'œuvre
    private final double bruit;
    private final Rarete rangMaxParGrade;
    private final double[] loiBouton; // parts de Brute, Correcte, Fine (le reste : Fine)

    public Qualite(Raretes raretes, double[] seuils, double bruit, Rarete rangMaxParGrade, double[] loiBouton) {
        this.raretes = raretes;
        this.seuils = seuils;
        this.bruit = bruit;
        this.rangMaxParGrade = rangMaxParGrade;
        this.loiBouton = loiBouton;
    }

    /** Valeurs de Babel : 30 / 55 / 70 / 85, aléa de 5 %, +1 rang jusqu'au Légendaire, bouton 45 / 50 / 5. */
    public static Qualite babel(Raretes raretes) {
        return new Qualite(raretes, new double[]{30, 55, 70, 85}, 0.05, raretes.parId("legendaire"), new double[]{45, 50, 5});
    }

    public static Qualite lire(Raretes raretes, ConfigurationSection c) {
        if (c == null) return babel(raretes);
        double[] seuils = {c.getDouble("seuils.correcte", 30), c.getDouble("seuils.fine", 55), c.getDouble("seuils.excellente", 70), c.getDouble("seuils.chef-d-oeuvre", 85)};
        Rarete max = raretes.parId(c.getString("rang-max-par-grade", "legendaire"));
        double[] loi = {c.getDouble("bouton.brute", 45), c.getDouble("bouton.correcte", 50), c.getDouble("bouton.fine", 5)};
        return new Qualite(raretes, seuils, Math.max(0, c.getDouble("bruit-stats", 0.05)), max == null ? raretes.dernier() : max, loi);
    }

    public Grade grade(double score) {
        if (score >= seuils[3]) return Grade.CHEF_D_OEUVRE;
        if (score >= seuils[2]) return Grade.EXCELLENTE;
        if (score >= seuils[1]) return Grade.FINE;
        if (score >= seuils[0]) return Grade.CORRECTE;
        return Grade.BRUTE;
    }

    /**
     * Calcule l'objet obtenu.
     *
     * @param score          score final de fabrication, de 0 à 100
     * @param rangMateriau   rang du matériau principal de la recette
     * @param chanceRangSup  chance (0 à 1) qu'un Chef-d'œuvre gagne un rang ; 0 = jamais
     * @param ecart          étages entre la recette et le bas du rang de maîtrise du fabricant (0 pour une recette de son rang)
     * @param hasard         source d'aléa (tirage du +1 rang, bruit des stats)
     */
    public Resultat calculer(double score, Rarete rangMateriau, double chanceRangSup, double ecart, Random hasard) {
        double s = Math.max(0, Math.min(100, score));
        Grade grade = grade(s);
        // le score exigé monte de 0,5 point par étage d'écart, seulement pour les rangs qui demandent plus que Correcte
        double exigence = rangMateriau.scoreExige() <= seuils[0] ? rangMateriau.scoreExige() : Math.min(100, rangMateriau.scoreExige() + 0.5 * Math.max(0, ecart));
        Rarete rang;
        boolean rebut = false;
        double base;
        Rarete dessus = raretes.suivant(rangMateriau);
        if (s < exigence) {
            Rarete dessous = raretes.precedent(rangMateriau);
            rebut = dessous == null;
            rang = dessous == null ? rangMateriau : dessous;
            base = exigence <= 0 ? 0 : s / exigence;
        } else if (grade == Grade.CHEF_D_OEUVRE && dessus != null && dessus.ordre() <= rangMaxParGrade.ordre()
            && chanceRangSup > 0 && hasard.nextDouble() < chanceRangSup) {
            rang = dessus;
            base = 0.3 * (s - seuils[3]) / Math.max(1, 100 - seuils[3]);
        } else {
            rang = rangMateriau;
            base = exigence >= 100 ? 1 : (s - exigence) / (100 - exigence);
        }
        return new Resultat(grade, rang, rebut, reflechir(base + hasard.nextGaussian() * bruit), s);
    }

    /** Score d'une fabrication au bouton : celui d'un débutant (45 % Brute, 50 % Correcte, 5 % Fine), jamais mieux que Fine. */
    public double scoreBouton(Random hasard) {
        double total = loiBouton[0] + loiBouton[1] + loiBouton[2];
        double t = hasard.nextDouble() * (total <= 0 ? 1 : total);
        double bas, haut;
        if (t < loiBouton[0]) { bas = 0; haut = seuils[0]; }
        else if (t < loiBouton[0] + loiBouton[1]) { bas = seuils[0]; haut = seuils[1]; }
        else { bas = seuils[1]; haut = seuils[2]; }
        return bas + hasard.nextDouble() * (haut - bas - 0.01);
    }

    /** Ramène une valeur dans [0 ; 1] par réflexion sur les bords (au lieu de l'écraser dessus). */
    public static double reflechir(double v) {
        double x = v;
        for (int i = 0; i < 4 && (x < 0 || x > 1); i++) {
            if (x < 0) x = -x;
            if (x > 1) x = 2 - x;
        }
        return Math.max(0, Math.min(1, x));
    }
}

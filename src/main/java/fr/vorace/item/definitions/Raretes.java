package fr.vorace.item.definitions;

import fr.vorace.item.Textes;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Les rangs de rareté et leurs fourchettes de stats.
 * <p>
 * Chaque stat d'un objet a une <b>valeur de référence</b> V (au rang Commun). La fourchette du rang r va de
 * V × multiplicateur(r) à V × multiplicateur(r + 1) : les fourchettes de deux rangs voisins se touchent, sans trou ni
 * recouvrement. Sous le Commun, la fourchette « rebut » (objets Bruts de matériau Commun) ; au-dessus du dernier rang,
 * le « sommet ».
 */
public final class Raretes {

    private final List<Rarete> rangs;
    private final Map<String, Rarete> parCle = new HashMap<>();
    private final double rebut;
    private final double sommet;
    private final double palierMultiplicateur;

    public Raretes(List<Rarete> rangs, double rebut, double sommet, double palierMultiplicateur) {
        this.rangs = List.copyOf(rangs);
        this.rebut = rebut;
        this.sommet = sommet;
        this.palierMultiplicateur = palierMultiplicateur;
        for (Rarete r : rangs) {
            parCle.put(r.id(), r);
            parCle.put(Textes.cle(r.nom()), r);
            parCle.put(Textes.cle(r.feminin()), r);
        }
    }

    public static Raretes lire(ConfigurationSection c, List<String> problemes) {
        List<Rarete> liste = new ArrayList<>();
        ConfigurationSection s = c == null ? null : c.getConfigurationSection("rangs");
        if (s != null) {
            int ordre = 0;
            double precedent = 0;
            for (String id : s.getKeys(false)) {
                ConfigurationSection r = s.getConfigurationSection(id);
                if (r == null) continue;
                double mult = r.getDouble("multiplicateur", 1.0);
                if (mult <= precedent) {
                    problemes.add("raretes.yml : le multiplicateur de « " + id + " » doit être plus grand que celui du rang précédent");
                    mult = precedent + 0.01;
                }
                precedent = mult;
                String nom = r.getString("nom", Textes.majuscule(id));
                liste.add(new Rarete(Textes.cle(id), ordre++, nom, r.getString("feminin", nom), r.getString("couleur", "#ffffff"),
                    mult, Math.max(0, r.getInt("lignes-bonus", 0)), Math.max(0, r.getInt("gemmes", 0)), r.getDouble("score-exige", 30)));
            }
        }
        if (liste.isEmpty()) {
            problemes.add("raretes.yml : aucun rang, les 7 rangs de Babel sont utilisés");
            liste = parDefaut();
        }
        double premier = liste.get(0).multiplicateur();
        double dernier = liste.get(liste.size() - 1).multiplicateur();
        double rebut = c == null ? premier * 0.85 : c.getDouble("rebut", premier * 0.85);
        double sommet = c == null ? dernier + 0.2 : c.getDouble("sommet", dernier + 0.2);
        if (rebut >= premier) rebut = premier * 0.85;
        if (sommet <= dernier) sommet = dernier + 0.2;
        return new Raretes(liste, rebut, sommet, c == null ? 1.12 : c.getDouble("palier-multiplicateur", 1.12));
    }

    /** Les 7 rangs de Babel (utilisés aussi par les tests). */
    public static List<Rarete> parDefaut() {
        return List.of(
            new Rarete("commun", 0, "Commun", "Commune", "#c8c8c8", 1.0, 0, 0, 30),
            new Rarete("peu_commun", 1, "Peu commun", "Peu commune", "#3a9e5c", 1.1, 1, 0, 30),
            new Rarete("rare", 2, "Rare", "Rare", "#4d8dff", 1.2, 1, 1, 30),
            new Rarete("epique", 3, "Épique", "Épique", "#b05cff", 1.35, 2, 1, 30),
            new Rarete("legendaire", 4, "Légendaire", "Légendaire", "#ffa42e", 1.5, 3, 2, 70),
            new Rarete("mythique", 5, "Mythique", "Mythique", "#ff4d5e", 1.7, 3, 2, 70),
            new Rarete("divin", 6, "Divin", "Divine", "#ffe680", 1.9, 4, 3, 85));
    }

    public static Raretes babel() {
        return new Raretes(parDefaut(), 0.85, 2.1, 1.12);
    }

    public List<Rarete> tous() { return rangs; }
    public Rarete premier() { return rangs.get(0); }
    public Rarete dernier() { return rangs.get(rangs.size() - 1); }

    /** Rang par son identifiant ou son nom (« peu_commun », « Peu commune », « épique »...), ou null. */
    public Rarete parId(String nom) {
        return nom == null ? null : parCle.get(Textes.cle(nom));
    }

    public Rarete parOrdre(int ordre) {
        return rangs.get(Math.max(0, Math.min(rangs.size() - 1, ordre)));
    }

    /** Rang du dessus, ou null au dernier rang. */
    public Rarete suivant(Rarete r) {
        return r.ordre() + 1 < rangs.size() ? rangs.get(r.ordre() + 1) : null;
    }

    /** Rang du dessous, ou null au premier rang. */
    public Rarete precedent(Rarete r) {
        return r.ordre() > 0 ? rangs.get(r.ordre() - 1) : null;
    }

    /** Multiplicateur d'un palier de 10 étages : 1 au palier 1, × 1,12 par palier ensuite (réglable). */
    public double multiplicateurPalier(int palier) {
        return Math.pow(palierMultiplicateur, Math.max(0, palier - 1));
    }

    /** Palier d'un étage : 1 pour les étages 1 à 10, 2 pour 11 à 20... */
    public static int palier(int etage) {
        return Math.max(1, (Math.max(1, etage) - 1) / 10 + 1);
    }

    /**
     * Fourchette {minimum, maximum} d'une stat à ce rang.
     *
     * @param reference valeur de référence au rang Commun (bas de la fourchette Commune)
     * @param rebut     vrai pour la fourchette « rebut », sous le premier rang (le rang est alors ignoré)
     */
    public double[] fourchette(double reference, Rarete r, boolean rebut) {
        double v = reference / premier().multiplicateur();
        if (rebut) return new double[]{v * this.rebut, v * premier().multiplicateur()};
        Rarete suivant = suivant(r);
        return new double[]{v * r.multiplicateur(), v * (suivant == null ? sommet : suivant.multiplicateur())};
    }

    /** Valeur d'une stat à cette position (0 à 1) de la fourchette. */
    public double valeur(double reference, Rarete r, boolean rebut, double position) {
        double[] f = fourchette(reference, r, rebut);
        double p = Math.max(0, Math.min(1, position));
        return f[0] + p * (f[1] - f[0]);
    }
}

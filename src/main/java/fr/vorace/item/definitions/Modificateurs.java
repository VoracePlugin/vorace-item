package fr.vorace.item.definitions;

import fr.vorace.item.Textes;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Les stats et modificateurs d'équipement (modificateurs.yml) : les 4 stats d'objet de Babel (Attaque physique,
 * Puissance magique, Armure physique, Armure magique) et les 38 modificateurs des 9 familles.
 */
public final class Modificateurs {

    /** Stats d'objet de Babel (celles qui suivent la fourchette du rang). */
    public static final String ATTAQUE = "attaque";
    public static final String PUISSANCE = "puissance";
    public static final String ARMURE = "armor";
    public static final String ARMURE_MAGIQUE = "magic-armor";

    /** Éléments de Babel (types d'attaque magiques) : identifiant → nom affiché. */
    public static final Map<String, String> ELEMENTS = elements();

    /**
     * @param pourcent  vrai si la valeur est en points de % (chances, conversions, vitesses)
     * @param bonusMin  valeur la plus basse d'une ligne bonus au palier 1
     * @param bonusMax  valeur la plus haute d'une ligne bonus au palier 1
     * @param parPalier croissance de la ligne bonus par palier (0,12 = +12 % par palier)
     */
    public record Modificateur(String id, String nom, String famille, boolean pourcent, double bonusMin, double bonusMax,
                               double parPalier) {}

    private final Map<String, Modificateur> parId = new LinkedHashMap<>();
    private final Map<String, String> alias = new HashMap<>();

    public static Modificateurs lire(ConfigurationSection c, List<String> problemes) {
        Modificateurs m = new Modificateurs();
        if (c != null) {
            for (String id : c.getKeys(false)) {
                ConfigurationSection s = c.getConfigurationSection(id);
                if (s == null) continue;
                String propre = id.toLowerCase(java.util.Locale.ROOT).trim();
                double min = 0, max = 0;
                List<Double> bonus = s.getDoubleList("bonus");
                if (bonus.size() >= 2) { min = bonus.get(0); max = bonus.get(1); }
                else if (bonus.size() == 1) { min = max = bonus.get(0); }
                if (max < min) { double t = min; min = max; max = t; }
                m.parId.put(propre, new Modificateur(propre, s.getString("nom", propre), s.getString("famille", "divers"),
                    s.getBoolean("pourcent", false), min, max, s.getDouble("par-palier", 0.12)));
                for (String a : s.getStringList("alias")) m.alias.put(Textes.cle(a), propre);
            }
        }
        for (String[] base : new String[][]{{ATTAQUE, "Attaque physique"}, {PUISSANCE, "Puissance magique"},
            {ARMURE, "Armure physique"}, {ARMURE_MAGIQUE, "Armure magique"}}) {
            if (!m.parId.containsKey(base[0])) {
                problemes.add("modificateurs.yml : « " + base[0] + " » manque, ajouté avec ses valeurs par défaut");
                m.parId.put(base[0], new Modificateur(base[0], base[1], "objet", false, 0, 0, 0.12));
            }
        }
        m.alias.putIfAbsent("armure", ARMURE);
        m.alias.putIfAbsent("armure-magique", ARMURE_MAGIQUE);
        m.alias.putIfAbsent("armure_magique", ARMURE_MAGIQUE);
        return m;
    }

    public List<Modificateur> tous() { return new ArrayList<>(parId.values()); }

    /** Identifiant canonique (« armure » → « armor », « feu-damage » inchangé), en minuscules. */
    public String id(String nom) {
        if (nom == null) return "";
        String n = nom.toLowerCase(java.util.Locale.ROOT).trim();
        if (parId.containsKey(n)) return n;
        String a = alias.get(Textes.cle(n));
        return a == null ? n : a;
    }

    public Modificateur parId(String nom) {
        return parId.get(id(nom));
    }

    public String nom(String id) {
        Modificateur m = parId(id);
        return m == null ? id : m.nom();
    }

    public boolean pourcent(String id) {
        Modificateur m = parId(id);
        return m != null && m.pourcent();
    }

    /** « +5 % Chance de coup critique », « +3 Dégâts physiques », « −2 Armure physique ». */
    public String afficher(String id, double valeur) {
        return (valeur < 0 ? "−" : "+") + Textes.nombre(Math.abs(valeur)) + (pourcent(id) ? " % " : " ") + nom(id);
    }

    /** Valeur tirée pour une ligne bonus de ce modificateur, à ce palier (0 si le modificateur n'a pas de plage). */
    public double tirerBonus(String id, int palier, Random hasard, double facteurRang) {
        Modificateur m = parId(id);
        if (m == null || (m.bonusMin() == 0 && m.bonusMax() == 0)) return 0;
        double brut = m.bonusMin() + hasard.nextDouble() * (m.bonusMax() - m.bonusMin());
        double v = brut * (1 + m.parPalier() * Math.max(0, palier - 1)) * facteurRang;
        // les chances et conversions restent lisibles : une décimale ; les valeurs en flat : arrondies à l'unité au-dessus de 10
        double arrondi = m.pourcent() || v < 10 ? Math.round(v * 10.0) / 10.0 : Math.round(v);
        return arrondi == 0 ? (m.pourcent() ? 0.1 : 1) : arrondi;
    }

    private static Map<String, String> elements() {
        Map<String, String> e = new LinkedHashMap<>();
        e.put("fire", "Feu");
        e.put("wind", "Vent");
        e.put("water", "Eau");
        e.put("earth", "Terre");
        e.put("ice", "Glace");
        e.put("lightning", "Foudre");
        e.put("light", "Lumière");
        e.put("dark", "Ténèbres");
        e.put("holy", "Sacré");
        e.put("corruption", "Corruption");
        return java.util.Collections.unmodifiableMap(e);
    }

    /** Identifiant d'élément à partir de son nom anglais ou français (« feu » → « fire »), ou null. */
    public static String element(String nom) {
        if (nom == null || nom.isBlank()) return null;
        String n = Textes.cle(nom);
        if (ELEMENTS.containsKey(n)) return n;
        for (Map.Entry<String, String> e : ELEMENTS.entrySet()) if (Textes.cle(e.getValue()).equals(n)) return e.getKey();
        return null;
    }
}

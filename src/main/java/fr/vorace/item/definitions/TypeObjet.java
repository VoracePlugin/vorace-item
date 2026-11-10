package fr.vorace.item.definitions;

import fr.vorace.item.Textes;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Un type d'objet (types.yml) : épée, dague, bâton, plastron, gemme... Il donne les valeurs par défaut de tous les
 * objets de ce type (emplacement, objet Minecraft de base, stats de scale, lignes bonus possibles).
 *
 * @param categorie     arme, armure, gemme, pierre (d'amélioration) ou materiau
 * @param emplacement   main, seconde-main, tete, torse, jambes, pieds ou aucun
 * @param nature        physique ou magique (formule de dégâts, critique possible ou non)
 * @param typeAttaque   Destruction, Impact, Cutting, Piercing, Shock (affiché sur l'objet)
 * @param scales        les deux stats de scale et leur lettre (S, A, B, C, D, E)
 * @param vitesseAttaque coups par seconde (4 = pas de temps de recharge entre deux coups)
 * @param empilable     vrai pour les objets sans exemplaire (gemmes, pierres, matériaux)
 */
public record TypeObjet(String id, String nom, String categorie, String emplacement, String materiau, String nature,
                        String typeAttaque, Map<String, String> scales, double vitesseAttaque, List<String> lignesBonus,
                        boolean empilable) {

    public static TypeObjet lire(String id, ConfigurationSection s) {
        String categorie = Textes.cle(s.getString("categorie", "arme"));
        boolean arme = categorie.equals("arme");
        boolean armure = categorie.equals("armure");
        return new TypeObjet(id, s.getString("nom", Textes.majuscule(id)), categorie,
            emplacement(s.getString("emplacement", arme ? "main" : "aucun")),
            s.getString("materiau", arme ? "iron_sword" : "paper").toLowerCase(Locale.ROOT).replace("minecraft:", ""),
            Textes.cle(s.getString("nature", "physique")).startsWith("mag") ? "magique" : "physique",
            s.getString("type-attaque", ""), scales(s.getConfigurationSection("scales")),
            s.getDouble("vitesse-attaque", arme ? 1.6 : 0), s.getStringList("lignes-bonus"),
            s.getBoolean("empilable", !arme && !armure));
    }

    public boolean estArme() { return categorie.equals("arme"); }
    public boolean estArmure() { return categorie.equals("armure"); }
    public boolean estGemme() { return categorie.equals("gemme"); }
    public boolean estPierre() { return categorie.equals("pierre"); }
    public boolean magique() { return nature.equals("magique"); }

    public static Map<String, String> scales(ConfigurationSection s) {
        Map<String, String> m = new LinkedHashMap<>();
        if (s != null) for (String k : s.getKeys(false)) m.put(stat(k), String.valueOf(s.get(k)).toUpperCase(Locale.ROOT).trim());
        return m;
    }

    /** Nom de stat du joueur : force, intelligence, constitution ou agilite (« FOR », « agilité »... acceptés). */
    public static String stat(String nom) {
        String n = Textes.cle(nom);
        if (n.startsWith("for") || n.equals("str")) return "force";
        if (n.startsWith("int")) return "intelligence";
        if (n.startsWith("con")) return "constitution";
        if (n.startsWith("agi") || n.equals("dex")) return "agilite";
        return n;
    }

    /** Abréviation affichée : FOR, INT, CON, AGI. */
    public static String abreviation(String stat) {
        return switch (stat) {
            case "force" -> "FOR";
            case "intelligence" -> "INT";
            case "constitution" -> "CON";
            case "agilite" -> "AGI";
            default -> stat.toUpperCase(Locale.ROOT);
        };
    }

    /** Coefficient d'une lettre de scale : S 1,5 ; A 1,2 ; B 1,0 ; C 0,75 ; D 0,5 ; E 0,25. Un nombre est pris tel quel. */
    public static double coefficient(String lettre) {
        if (lettre == null) return 0;
        return switch (lettre.trim().toUpperCase(Locale.ROOT)) {
            case "S" -> 1.5;
            case "A" -> 1.2;
            case "B" -> 1.0;
            case "C" -> 0.75;
            case "D" -> 0.5;
            case "E" -> 0.25;
            default -> {
                try {
                    yield Double.parseDouble(lettre.trim().replace(',', '.'));
                } catch (NumberFormatException e) {
                    yield 0;
                }
            }
        };
    }

    public static String emplacement(String nom) {
        String n = Textes.cle(nom).replace('_', '-');
        return switch (n) {
            case "main", "main-droite", "mainhand" -> "main";
            case "seconde-main", "main-gauche", "offhand", "bouclier" -> "seconde-main";
            case "tete", "casque", "head" -> "tete";
            case "torse", "plastron", "chest" -> "torse";
            case "jambes", "jambieres", "legs" -> "jambes";
            case "pieds", "bottes", "feet" -> "pieds";
            default -> "aucun";
        };
    }
}

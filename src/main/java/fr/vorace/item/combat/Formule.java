package fr.vorace.item.combat;

import fr.vorace.item.definitions.TypeObjet;

import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * La formule de dégâts de Babel, la même pour le physique et la magie :
 * <pre>
 * PUISSANCE = (Attaque ou Puissance de l'arme + scale1 × stat1 + scale2 × stat2) × multiplicateur de compétence
 * DÉFENSE   = Résistance + Armure du même type
 * DÉGÂTS    = PUISSANCE² ÷ (PUISSANCE + DÉFENSE)
 * </pre>
 */
public final class Formule {

    private Formule() {}

    /** Puissance du coup. Les lettres de scale valent S 1,5 ; A 1,2 ; B 1,0 ; C 0,75 ; D 0,5 ; E 0,25. */
    public static double puissance(double arme, Map<String, String> scales, ToDoubleFunction<String> stat, double bonus, double multiplicateur) {
        double p = arme + bonus;
        for (Map.Entry<String, String> e : scales.entrySet()) p += TypeObjet.coefficient(e.getValue()) * stat.applyAsDouble(e.getKey());
        return Math.max(0, p) * multiplicateur;
    }

    /** Dégâts de base : la part qui passe est Puissance ÷ (Puissance + Défense), elle ne tombe jamais à zéro. */
    public static double degats(double puissance, double defense) {
        if (puissance <= 0) return 0;
        return puissance * puissance / (puissance + Math.max(0, defense));
    }

    /**
     * Part des dégâts selon le temps écoulé depuis le coup précédent, comme dans le jeu de base : frapper sans
     * attendre la recharge de l'arme donne 20 % des dégâts, un coup pleinement rechargé 100 %.
     *
     * @param tics           tics écoulés depuis le coup précédent
     * @param vitesseAttaque coups par seconde de l'arme
     */
    public static double charge(double tics, double vitesseAttaque) {
        if (vitesseAttaque <= 0) return 1;
        double c = Math.max(0, Math.min(1, (tics + 0.5) / (20.0 / vitesseAttaque)));
        return 0.2 + c * c * 0.8;
    }

    /** Arrondi final : à l'entier le plus proche, au moins 1 dès que le coup porte. */
    public static double arrondir(double degats) {
        return degats <= 0 ? 0 : Math.max(1, Math.round(degats));
    }
}

package fr.vorace.item.definitions;

/**
 * Un rang de rareté (raretes.yml). Les 7 rangs de Babel : Commun, Peu commun, Rare, Épique, Légendaire, Mythique, Divin.
 *
 * @param multiplicateur bas de la fourchette de stats de ce rang, par rapport à la valeur de référence au rang Commun
 * @param lignesBonus    nombre de modificateurs tirés en plus sur un objet de ce rang
 * @param gemmes         nombre d'emplacements de gemmes par défaut
 * @param scoreExige     score de fabrication en dessous duquel l'objet sort au rang inférieur
 */
public record Rarete(String id, int ordre, String nom, String feminin, String couleur, double multiplicateur,
                     int lignesBonus, int gemmes, double scoreExige) {

    /** Balise MiniMessage de la couleur du rang. */
    public String balise() {
        return "<" + couleur + ">";
    }
}

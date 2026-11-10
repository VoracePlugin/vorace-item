package fr.vorace.item.api;

import org.bukkit.entity.Player;

/**
 * Le mana et la stamina des joueurs, dépensés par les compétences d'armes. Le plugin des classes peut fournir les
 * siens (même enregistrement que {@link ProfilJoueur}) ; sinon vorace-item tient ses propres jauges.
 */
public interface RessourcesJoueur {

    double mana(Player joueur);

    double manaMax(Player joueur);

    double stamina(Player joueur);

    double staminaMax(Player joueur);

    /** Retire le mana et la stamina demandés seulement si le joueur a les deux ; faux (et rien de retiré) sinon. */
    boolean depenser(Player joueur, double mana, double stamina);

    /** Rend du mana (vol de mana, potion...). */
    void rendreMana(Player joueur, double mana);
}

package fr.vorace.item.api;

import org.bukkit.entity.LivingEntity;

/**
 * Défense d'un monstre (Résistance + Armure du type demandé) dans la formule de dégâts. vorace-mob ou le plugin des
 * monstres peut fournir la sienne avec {@link VoraceItemAPI#defenseMonstres(DefenseMonstre)}.
 */
@FunctionalInterface
public interface DefenseMonstre {

    /** @param magique vrai pour un coup magique, faux pour un coup physique */
    double defense(LivingEntity monstre, boolean magique);
}

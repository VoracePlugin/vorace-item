package fr.vorace.item.api;

import org.bukkit.entity.Player;

/**
 * Les stats d'un joueur qui ne viennent pas de son équipement : c'est le plugin des classes qui les connaît.
 * Il s'enregistre auprès du serveur :
 * <pre>
 * Bukkit.getServicesManager().register(ProfilJoueur.class, monProfil, monPlugin, ServicePriority.Normal);
 * </pre>
 * Tant qu'aucun plugin ne le fait, vorace-item utilise les valeurs de « profil-par-defaut » (config.yml), réglables
 * joueur par joueur avec « /vitem profil ».
 */
public interface ProfilJoueur {

    /** Stat du joueur : « force », « intelligence », « constitution » ou « agilite ». */
    double stat(Player joueur, String stat);

    /** Niveau du joueur (conditions de niveau des objets). */
    int niveau(Player joueur);

    /** Vrai si le joueur a cette classe (ou une classe qui en découle). L'identifiant est en minuscules, sans accent. */
    boolean aClasse(Player joueur, String classe);

    /** Résistance physique, hors armure des objets. */
    double resistancePhysique(Player joueur);

    /** Résistance magique, hors armure magique des objets. */
    double resistanceMagique(Player joueur);

    /** Chance de coup critique en %, hors modificateurs des objets. */
    double critique(Player joueur);

    /** Chance d'esquive en %, hors modificateurs des objets. */
    double esquive(Player joueur);
}

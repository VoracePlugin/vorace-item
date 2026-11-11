package fr.vorace.item.api;

import org.bukkit.entity.Player;

/**
 * Ce que vorace-item doit savoir d'un joueur pour les conditions des objets (niveau, classe) : c'est le plugin des
 * classes qui le connaît. Il s'enregistre auprès du serveur :
 * <pre>
 * Bukkit.getServicesManager().register(ProfilJoueur.class, monProfil, monPlugin, ServicePriority.Normal);
 * </pre>
 * Tant qu'aucun plugin ne le fait, vorace-item prend le niveau d'expérience du jeu de base (ou « profil-par-defaut »
 * de config.yml) et les classes données par permission ou par « /vitem profil ».
 */
public interface ProfilJoueur {

    /** Niveau du joueur (conditions de niveau des objets). */
    int niveau(Player joueur);

    /** Vrai si le joueur a cette classe (ou une classe qui en découle). L'identifiant est en minuscules, sans accent. */
    boolean aClasse(Player joueur, String classe);
}

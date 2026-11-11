package fr.vorace.item.equipement;

import fr.vorace.item.Textes;
import fr.vorace.item.VoraceItem;
import fr.vorace.item.api.ProfilJoueur;
import fr.vorace.item.exemplaire.Exemplaires;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Profil utilisé tant qu'aucun plugin de classes n'en fournit un, pour les conditions des objets. Le niveau est celui
 * d'expérience du jeu de base (ou « profil-par-defaut.niveau » de config.yml), remplacé joueur par joueur par
 * « /vitem profil » ; les classes viennent de la permission « voraceitem.classe.&lt;classe&gt; » ou de
 * « /vitem profil &lt;joueur&gt; classes ».
 */
public final class ProfilParDefaut implements ProfilJoueur {

    private final VoraceItem plugin;
    private final NamespacedKey cleNiveau = Exemplaires.cle("profil_niveau");
    private final NamespacedKey cleClasses = Exemplaires.cle("profil_classes");

    public ProfilParDefaut(VoraceItem plugin) {
        this.plugin = plugin;
    }

    /** Fixe le niveau de ce joueur (null = revenir au niveau du jeu de base ou de config.yml). */
    public void fixerNiveau(Player joueur, Integer niveau) {
        if (niveau == null) joueur.getPersistentDataContainer().remove(cleNiveau);
        else joueur.getPersistentDataContainer().set(cleNiveau, PersistentDataType.DOUBLE, niveau.doubleValue());
    }

    public String classes(Player joueur) {
        return joueur.getPersistentDataContainer().getOrDefault(cleClasses, PersistentDataType.STRING, "");
    }

    public void fixerClasses(Player joueur, String classes) {
        if (classes == null || classes.isBlank()) joueur.getPersistentDataContainer().remove(cleClasses);
        else joueur.getPersistentDataContainer().set(cleClasses, PersistentDataType.STRING, classes);
    }

    /** Vrai si le niveau de ce joueur a été fixé par « /vitem profil ». */
    public boolean niveauFixe(Player joueur) {
        return joueur.getPersistentDataContainer().has(cleNiveau, PersistentDataType.DOUBLE);
    }

    @Override
    public int niveau(Player joueur) {
        Double fixe = joueur.getPersistentDataContainer().get(cleNiveau, PersistentDataType.DOUBLE);
        if (fixe != null) return (int) Math.round(fixe);
        return plugin.reglages().niveauMinecraft ? joueur.getLevel() : plugin.reglages().niveauParDefaut;
    }

    @Override
    public boolean aClasse(Player joueur, String classe) {
        String c = Textes.cle(classe);
        if (joueur.hasPermission("voraceitem.classe." + c)) return true;
        for (String x : classes(joueur).split(",")) if (Textes.cle(x).equals(c)) return true;
        return false;
    }
}

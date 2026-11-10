package fr.vorace.item.combat;

import fr.vorace.item.Textes;
import fr.vorace.item.VoraceItem;
import fr.vorace.item.api.ProfilJoueur;
import fr.vorace.item.definitions.TypeObjet;
import fr.vorace.item.exemplaire.Exemplaires;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Profil utilisé tant qu'aucun plugin de classes n'en fournit un : les valeurs de « profil-par-defaut » (config.yml),
 * remplacées joueur par joueur par « /vitem profil » (gardées dans le joueur). Les classes viennent de la permission
 * « voraceitem.classe.&lt;classe&gt; » ou de « /vitem profil &lt;joueur&gt; classes ».
 */
public final class ProfilParDefaut implements ProfilJoueur {

    public static final List<String> STATS = List.of("force", "intelligence", "constitution", "agilite", "niveau",
        "resistance-physique", "resistance-magique", "critique", "esquive");

    private final VoraceItem plugin;
    private final NamespacedKey cleClasses = Exemplaires.cle("profil_classes");

    public ProfilParDefaut(VoraceItem plugin) {
        this.plugin = plugin;
    }

    private static NamespacedKey cle(String stat) {
        return Exemplaires.cle("profil_" + stat.replace('-', '_'));
    }

    /** Valeur fixée pour ce joueur, sinon celle de config.yml. */
    public double valeur(Player joueur, String stat) {
        Double v = joueur.getPersistentDataContainer().get(cle(stat), PersistentDataType.DOUBLE);
        return v != null ? v : plugin.reglages().profil.getOrDefault(stat, 0.0);
    }

    /** Fixe une stat pour ce joueur (null = revenir à la valeur de config.yml). */
    public void fixer(Player joueur, String stat, Double valeur) {
        if (valeur == null) joueur.getPersistentDataContainer().remove(cle(stat));
        else joueur.getPersistentDataContainer().set(cle(stat), PersistentDataType.DOUBLE, valeur);
    }

    public String classes(Player joueur) {
        return joueur.getPersistentDataContainer().getOrDefault(cleClasses, PersistentDataType.STRING, "");
    }

    public void fixerClasses(Player joueur, String classes) {
        if (classes == null || classes.isBlank()) joueur.getPersistentDataContainer().remove(cleClasses);
        else joueur.getPersistentDataContainer().set(cleClasses, PersistentDataType.STRING, classes);
    }

    @Override
    public double stat(Player joueur, String stat) {
        return valeur(joueur, TypeObjet.stat(stat));
    }

    @Override
    public int niveau(Player joueur) {
        Double fixe = joueur.getPersistentDataContainer().get(cle("niveau"), PersistentDataType.DOUBLE);
        if (fixe != null) return (int) Math.round(fixe);
        return plugin.reglages().niveauMinecraft ? joueur.getLevel() : (int) Math.round(plugin.reglages().profil.getOrDefault("niveau", 1.0));
    }

    @Override
    public boolean aClasse(Player joueur, String classe) {
        String c = Textes.cle(classe);
        if (joueur.hasPermission("voraceitem.classe." + c)) return true;
        for (String x : classes(joueur).split(",")) if (Textes.cle(x).equals(c)) return true;
        return false;
    }

    @Override public double resistancePhysique(Player joueur) { return valeur(joueur, "resistance-physique"); }
    @Override public double resistanceMagique(Player joueur) { return valeur(joueur, "resistance-magique"); }
    @Override public double critique(Player joueur) { return valeur(joueur, "critique"); }
    @Override public double esquive(Player joueur) { return valeur(joueur, "esquive"); }
}

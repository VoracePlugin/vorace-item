package fr.vorace.item;

import org.bukkit.configuration.file.FileConfiguration;

/** Réglages de config.yml, lus une fois (« /vitem recharger » les relit). */
public final class Reglages {

    // conditions des objets, tant qu'aucun plugin de classes ne fournit le niveau des joueurs
    public final boolean niveauMinecraft;
    public final int niveauParDefaut;

    // durabilité et réparation
    public final int usureParUtilisation;
    public final double perteMort;
    public final double usureReparation;
    public final double plancherReparation;

    // amélioration (niveau posé sur l'objet par un autre plugin)
    public final double ameliorationBonus;

    // pack
    public final String dossierPack;
    public final boolean exemples;

    public Reglages(FileConfiguration c) {
        niveauMinecraft = c.getBoolean("profil-par-defaut.niveau-minecraft", true);
        niveauParDefaut = c.getInt("profil-par-defaut.niveau", 1);

        usureParUtilisation = Math.max(0, c.getInt("durabilite.usure-par-utilisation", c.getInt("durabilite.usure-par-coup", 1)));
        perteMort = Math.max(0, Math.min(1, c.getDouble("durabilite.perte-a-la-mort", 0.4)));
        usureReparation = Math.max(0, c.getDouble("reparation.usure-permanente", 0.005));
        plancherReparation = Math.max(0, Math.min(1, c.getDouble("reparation.plancher", 0.5)));

        ameliorationBonus = c.getDouble("amelioration.bonus-par-niveau", 0.03);

        dossierPack = c.getString("pack.dossier", "plugins/Vorace/pack/sources/voraceitem");
        exemples = c.getBoolean("exemples", true);
    }
}

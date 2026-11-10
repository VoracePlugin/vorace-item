package fr.vorace.item;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Réglages de config.yml, lus une fois (« /vitem recharger » les relit). */
public final class Reglages {

    // combat
    public final boolean combat;
    public final double critique;
    public final double esquiveMax;
    public final double balayage;
    public final boolean ignorerArmureVanilla;
    public final boolean monstresContreJoueurs;
    public final double attaqueMonstres;
    public final double defenseParArmureVanilla;
    public final double mainsNues;
    public final double coefArmureMagique;
    public final boolean chiffres;

    // profil par défaut (tant qu'aucun plugin de classes ne fournit les stats des joueurs)
    public final Map<String, Double> profil = new LinkedHashMap<>();
    public final boolean niveauMinecraft;

    // mana et stamina
    public final boolean ressources;
    public final double manaBase;
    public final double manaRegen;
    public final double staminaBase;
    public final double staminaRegen;
    public final boolean barreRessources;

    // durabilité et réparation
    public final int usureParCoup;
    public final double perteMort;
    public final double usureReparation;
    public final double plancherReparation;

    // amélioration
    public final int ameliorationMax;
    public final double ameliorationBonus;
    public final List<Double> ameliorationChances = new ArrayList<>();
    public final boolean ameliorationEchecBaisse;

    // gemmes
    public final boolean gemmeRetirable;

    // forge
    public final String forgeRangMax;
    public final List<String> forgeBlocs = new ArrayList<>();
    public final List<String> atelierBlocs = new ArrayList<>();
    public final double forgeSurcout;

    // butin
    public final boolean butin;
    public final double butinPositionMax;

    // pack
    public final String dossierPack;
    public final boolean exemples;

    public Reglages(FileConfiguration c) {
        combat = c.getBoolean("combat.actif", true);
        critique = c.getDouble("combat.multiplicateur-critique", 1.25);
        esquiveMax = c.getDouble("combat.esquive-max", 20);
        balayage = c.getDouble("combat.balayage", 0.5);
        ignorerArmureVanilla = c.getBoolean("combat.ignorer-armure-vanilla", true);
        monstresContreJoueurs = c.getBoolean("combat.monstres-contre-joueurs", true);
        attaqueMonstres = c.getDouble("combat.attaque-monstres", 5.0);
        defenseParArmureVanilla = c.getDouble("combat.defense-par-point-d-armure", 2.0);
        mainsNues = c.getDouble("combat.attaque-mains-nues", 1.0);
        coefArmureMagique = c.getDouble("combat.coefficient-armure-magique", 2.0);
        chiffres = c.getBoolean("combat.afficher-degats", false);

        for (String s : new String[]{"force", "intelligence", "constitution", "agilite", "niveau", "resistance-physique",
            "resistance-magique", "critique", "esquive"}) {
            profil.put(s, c.getDouble("profil-par-defaut." + s, s.equals("niveau") ? 1 : 0));
        }
        niveauMinecraft = c.getBoolean("profil-par-defaut.niveau-minecraft", true);

        ressources = c.getBoolean("ressources.actif", true);
        manaBase = c.getDouble("ressources.mana", 50);
        manaRegen = c.getDouble("ressources.regeneration-mana", 1);
        staminaBase = c.getDouble("ressources.stamina", 100);
        staminaRegen = c.getDouble("ressources.regeneration-stamina", 5);
        barreRessources = c.getBoolean("ressources.barre-d-action", true);

        usureParCoup = Math.max(0, c.getInt("durabilite.usure-par-coup", 1));
        perteMort = Math.max(0, Math.min(1, c.getDouble("durabilite.perte-a-la-mort", 0.4)));
        usureReparation = Math.max(0, c.getDouble("reparation.usure-permanente", 0.005));
        plancherReparation = Math.max(0, Math.min(1, c.getDouble("reparation.plancher", 0.5)));

        ameliorationMax = Math.max(0, c.getInt("amelioration.niveau-max", 10));
        ameliorationBonus = c.getDouble("amelioration.bonus-par-niveau", 0.03);
        for (Double d : c.getDoubleList("amelioration.chances")) ameliorationChances.add(Math.max(0, Math.min(100, d)));
        ameliorationEchecBaisse = c.getBoolean("amelioration.echec-baisse-le-niveau", false);

        gemmeRetirable = c.getBoolean("gemmes.retirable", false);

        forgeRangMax = c.getString("forge.rang-max", "rare");
        forgeBlocs.addAll(majuscules(c.getStringList("forge.blocs")));
        atelierBlocs.addAll(majuscules(c.getStringList("atelier.blocs")));
        forgeSurcout = Math.max(1, c.getDouble("forge.surcout", 1.0));

        butin = c.getBoolean("butin.actif", true);
        butinPositionMax = Math.max(0, Math.min(1, c.getDouble("butin.position-max", 0.5)));

        dossierPack = c.getString("pack.dossier", "plugins/Vorace/pack/sources/voraceitem");
        exemples = c.getBoolean("exemples", true);
    }

    /** Chance de réussite (0 à 1) du passage au niveau d'amélioration donné (1 = de +0 à +1). */
    public double chanceAmelioration(int niveau) {
        if (ameliorationChances.isEmpty()) return 1;
        int i = Math.max(0, Math.min(ameliorationChances.size() - 1, niveau - 1));
        return ameliorationChances.get(i) / 100.0;
    }

    private static List<String> majuscules(List<String> l) {
        List<String> r = new ArrayList<>();
        for (String s : l) r.add(s.toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""));
        return r;
    }

    static ConfigurationSection section(FileConfiguration c, String chemin) {
        return c.getConfigurationSection(chemin);
    }
}

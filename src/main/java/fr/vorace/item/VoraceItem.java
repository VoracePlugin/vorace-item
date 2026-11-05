package fr.vorace.item;

import fr.vorace.Vorace;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * vorace-item : objets RPG (stats, rareté, armes modulaires, forge, métiers), appuyés sur les objets de vorace-core.
 * En préparation : pour l'instant le plugin vérifie seulement que vorace-core est là (voir README, feuille de route).
 */
public final class VoraceItem extends JavaPlugin {

    private Vorace core;

    /** Le plugin vorace-core (objets, pack, modèles). */
    public Vorace core() { return core; }

    @Override
    public void onEnable() {
        if (!(Bukkit.getPluginManager().getPlugin("Vorace") instanceof Vorace v) || !v.isEnabled()) {
            getLogger().severe("vorace-item a besoin de vorace-core (plugin « Vorace ») : désactivé.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        core = v;
        getLogger().info("vorace-item " + getPluginMeta().getVersion() + " : en préparation (objets RPG à venir).");
    }
}

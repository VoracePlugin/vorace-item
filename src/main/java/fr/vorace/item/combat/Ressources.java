package fr.vorace.item.combat;

import fr.vorace.item.Reglages;
import fr.vorace.item.Textes;
import fr.vorace.item.VoraceItem;
import fr.vorace.item.api.RessourcesJoueur;
import fr.vorace.item.exemplaire.Exemplaires;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Jauges de mana et de stamina de vorace-item, utilisées tant qu'aucun plugin de classes ne fournit les siennes.
 * Maximum = base de config.yml + modificateurs de l'équipement ; régénération chaque seconde.
 */
public final class Ressources implements RessourcesJoueur {

    private static final class Jauges {
        double mana, stamina;
        int affichageJusqua;
        int secondes;
    }

    private final VoraceItem plugin;
    private final Map<UUID, Jauges> jauges = new HashMap<>();
    private final NamespacedKey cleMana = Exemplaires.cle("mana"), cleStamina = Exemplaires.cle("stamina");

    public Ressources(VoraceItem plugin) {
        this.plugin = plugin;
    }

    private Jauges de(Player p) {
        return jauges.computeIfAbsent(p.getUniqueId(), k -> {
            Jauges j = new Jauges();
            j.mana = Math.min(manaMax(p), p.getPersistentDataContainer().getOrDefault(cleMana, PersistentDataType.DOUBLE, Double.MAX_VALUE));
            j.stamina = Math.min(staminaMax(p), p.getPersistentDataContainer().getOrDefault(cleStamina, PersistentDataType.DOUBLE, Double.MAX_VALUE));
            return j;
        });
    }

    /** Garde les jauges dans le joueur (départ du joueur, arrêt du plugin). */
    public void enregistrer(Player p) {
        Jauges j = jauges.remove(p.getUniqueId());
        if (j == null) return;
        p.getPersistentDataContainer().set(cleMana, PersistentDataType.DOUBLE, j.mana);
        p.getPersistentDataContainer().set(cleStamina, PersistentDataType.DOUBLE, j.stamina);
    }

    @Override public double mana(Player p) { return de(p).mana; }
    @Override public double stamina(Player p) { return de(p).stamina; }

    @Override
    public double manaMax(Player p) {
        return Math.max(0, plugin.reglages().manaBase + plugin.equipement().bilan(p).total("max-mana"));
    }

    @Override
    public double staminaMax(Player p) {
        return Math.max(0, plugin.reglages().staminaBase + plugin.equipement().bilan(p).total("max-stamina"));
    }

    @Override
    public boolean depenser(Player p, double mana, double stamina) {
        Jauges j = de(p);
        if (j.mana + 1e-9 < mana || j.stamina + 1e-9 < stamina) return false;
        j.mana -= Math.max(0, mana);
        j.stamina -= Math.max(0, stamina);
        j.affichageJusqua = Bukkit.getCurrentTick() + 60;
        afficher(p, j);
        return true;
    }

    @Override
    public void rendreMana(Player p, double mana) {
        Jauges j = de(p);
        j.mana = Math.min(manaMax(p), j.mana + Math.max(0, mana));
    }

    @Override
    public void rendreStamina(Player p, double stamina) {
        Jauges j = de(p);
        j.stamina = Math.min(staminaMax(p), j.stamina + Math.max(0, stamina));
    }

    /** Appelé une fois par seconde : régénération du mana, de la stamina et de la vie apportée par l'équipement. */
    public void seconde() {
        Reglages r = plugin.reglages();
        boolean service = plugin.ressources() != this;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead()) continue;
            Equipement.Bilan b = plugin.equipement().bilan(p);
            Jauges j = de(p);
            j.secondes++;
            // régénération de vie : la valeur du modificateur est rendue toutes les 5 secondes
            double regen = b.total("health-regeneration");
            if (regen > 0 && j.secondes % 5 == 0) {
                AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                if (max != null && p.getHealth() < max.getValue()) p.setHealth(Math.min(max.getValue(), p.getHealth() + regen));
            }
            if (service || !r.ressources) continue;
            double manaMax = manaMax(p), staminaMax = staminaMax(p);
            boolean plein = j.mana >= manaMax && j.stamina >= staminaMax;
            j.mana = Math.min(manaMax, j.mana + Math.max(0, r.manaRegen + b.total("mana-regeneration")));
            j.stamina = Math.min(staminaMax, j.stamina + Math.max(0, r.staminaRegen + b.total("stamina-regeneration")));
            if (!plein && Bukkit.getCurrentTick() < j.affichageJusqua + 100) afficher(p, j);
        }
    }

    private void afficher(Player p, Jauges j) {
        if (!plugin.reglages().barreRessources) return;
        p.sendActionBar(Textes.objet("<#6fb7ff>✦ " + Textes.nombre(Math.floor(j.mana)) + "<#4a6f99>/" + Textes.nombre(manaMax(p))
            + " Mana   <#ffd166>⚡ " + Textes.nombre(Math.floor(j.stamina)) + "<#99804a>/" + Textes.nombre(staminaMax(p)) + " SP"));
    }
}

package fr.vorace.item.combat;

import fr.vorace.item.Reglages;
import fr.vorace.item.Textes;
import fr.vorace.item.VoraceItem;
import fr.vorace.item.api.DefenseMonstre;
import fr.vorace.item.api.ProfilJoueur;
import fr.vorace.item.api.VoraceItemDegatsEvent;
import fr.vorace.item.definitions.ModeleObjet;
import fr.vorace.item.definitions.Modificateurs;
import fr.vorace.item.exemplaire.Exemplaires;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Applique la formule de dégâts de Babel aux coups des joueurs (mêlée, tirs, compétences) et aux coups qu'ils reçoivent. */
public final class Combat implements Listener {

    /** Résultat du calcul d'un coup. */
    public record Frappe(double degats, boolean critique, boolean esquive, boolean brut, boolean magique) {}

    private record Tir(UUID tireur, Equipement.Bilan bilan, ModeleObjet arme, double multiplicateur, Boolean magique, String competence) {}

    private final VoraceItem plugin;
    private final Map<UUID, Integer> dernierCoup = new HashMap<>();
    private final Map<UUID, Tir> tirs = new HashMap<>();
    private final NamespacedKey cleResPhysique = Exemplaires.cle("resistance_physique"), cleResMagique = Exemplaires.cle("resistance_magique");
    private volatile DefenseMonstre defenseMonstres;
    private boolean direct;
    private double degatsDirects;

    public Combat(VoraceItem plugin) {
        this.plugin = plugin;
    }

    public void defenseMonstres(DefenseMonstre d) { this.defenseMonstres = d; }

    // ------------------------------------------------------------------ calcul

    /**
     * Calcule un coup d'un joueur.
     *
     * @param bilan            équipement de l'attaquant au moment du coup
     * @param arme             modèle de l'arme (null = mains nues)
     * @param multiplicateur   multiplicateur de compétence (1 pour un coup normal)
     * @param magiqueForce     true ou false pour imposer la nature du coup, null pour celle de l'arme
     */
    public Frappe calculer(Player attaquant, Equipement.Bilan bilan, ModeleObjet arme, LivingEntity cible, double multiplicateur, Boolean magiqueForce) {
        Reglages r = plugin.reglages();
        ProfilJoueur profil = plugin.profil();
        ThreadLocalRandom h = ThreadLocalRandom.current();
        boolean magique = magiqueForce != null ? magiqueForce : arme != null && arme.magique();

        Equipement.Bilan bilanCible = cible instanceof Player v ? plugin.equipement().bilan(v) : null;
        if (cible instanceof Player v) {
            double esquive = Math.min(r.esquiveMax, profil.esquive(v) + bilanCible.total("dodge-rating"));
            if (h.nextDouble() * 100 < esquive) return new Frappe(0, false, true, false, magique);
        }

        double base = arme == null ? r.mainsNues : bilan.total(magique ? Modificateurs.PUISSANCE : Modificateurs.ATTAQUE);
        Map<String, String> scales = arme == null ? Map.of("force", "D") : arme.scales();
        double puissance = Formule.puissance(base, scales, s -> profil.stat(attaquant, s),
            bilan.total(magique ? "magic-damage" : "physical-damage"), multiplicateur);

        boolean brut = h.nextDouble() * 100 < bilan.total("true-damage-chance");
        double defense = brut ? 0 : Math.max(0, defense(cible, magique) - bilan.total(magique ? "magic-penetration" : "physical-penetration"));
        double degats = Formule.degats(puissance, defense);

        // dégâts élémentaires des modificateurs : un second coup magique, contre la défense magique
        double elementaire = bilan.elementaire() * multiplicateur;
        if (elementaire > 0) degats += Formule.degats(elementaire, Math.max(0, defense(cible, true) - bilan.total("magic-penetration")));

        boolean critique = false;
        if (!magique && degats > 0 && h.nextDouble() * 100 < profil.critique(attaquant) + bilan.total("critical-strike-chance")) {
            critique = true;
            double mult = r.critique + bilan.total("critical-strike-power") / 100.0;
            if (bilanCible != null) mult -= bilanCible.total("critical-damage-reduction") / 100.0;
            degats *= Math.max(1, mult);
        }
        if (bilanCible != null) degats *= 1 - Math.min(80, Math.max(0, bilanCible.total("damage-reduction"))) / 100.0;
        return new Frappe(Formule.arrondir(degats), critique, false, brut, magique);
    }

    /** Défense d'une cible : Résistance + Armure du même type. */
    public double defense(LivingEntity cible, boolean magique) {
        Reglages r = plugin.reglages();
        if (cible instanceof Player p) {
            Equipement.Bilan b = plugin.equipement().bilan(p);
            return magique ? plugin.profil().resistanceMagique(p) + b.total(Modificateurs.ARMURE_MAGIQUE) * r.coefArmureMagique
                : plugin.profil().resistancePhysique(p) + b.total(Modificateurs.ARMURE);
        }
        DefenseMonstre d = defenseMonstres;
        if (d != null) return Math.max(0, d.defense(cible, magique));
        // valeur écrite sur le monstre par un autre plugin, sinon son armure du jeu de base
        Double fixe = cible.getPersistentDataContainer().get(magique ? cleResMagique : cleResPhysique, PersistentDataType.DOUBLE);
        if (fixe != null) return Math.max(0, fixe);
        AttributeInstance armure = cible.getAttribute(Attribute.ARMOR);
        double v = armure == null ? 0 : armure.getValue() * r.defenseParArmureVanilla;
        return magique ? v / 2 : v;
    }

    /**
     * Fait porter un coup calculé par vorace-item (compétences d'armes, autres plugins).
     *
     * @return les dégâts infligés (0 si le coup est esquivé ou annulé)
     */
    public double frapper(Player attaquant, LivingEntity cible, double multiplicateur, Boolean magique, String competence) {
        if (cible.isDead() || !cible.isValid() || cible.equals(attaquant)) return 0;
        Equipement.Bilan bilan = plugin.equipement().bilan(attaquant);
        ModeleObjet arme = bilan.arme() == null ? null : bilan.arme().modele();
        Frappe f = calculer(attaquant, bilan, arme, cible, multiplicateur, magique);
        double degats = avantCoup(attaquant, bilan, arme, cible, f, competence);
        if (degats <= 0) return 0;
        cible.setNoDamageTicks(0);
        direct = true;
        degatsDirects = degats;
        try {
            cible.damage(degats, attaquant);
        } finally {
            direct = false;
        }
        apresCoup(attaquant, bilan, cible, degats, false);
        return degats;
    }

    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void degats(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity cible)) return;
        if (direct) { fixer(e, degatsDirects); return; }
        if (!plugin.reglages().combat) return;
        Entity source = e.getDamager();
        EntityDamageEvent.DamageCause cause = e.getCause();

        if (source instanceof Player p && (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
            boolean balayage = cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK;
            Equipement.Bilan bilan = plugin.equipement().bilan(p);
            ModeleObjet arme = bilan.arme() == null ? null : bilan.arme().modele();
            if (arme == null && !plugin.reglages().formulePartout) {
                // sans arme de vorace-item, le coup garde sa force du jeu de base (épée en diamant, poing...)
                if (cible instanceof Player victime && plugin.reglages().monstresContreJoueurs && !plugin.equipement().bilan(victime).portes().isEmpty()) {
                    coupDuJeu(e, p, victime, 1);
                }
                return;
            }
            double mult = balayage ? plugin.reglages().balayage : charge(p);
            Frappe f = calculer(p, bilan, arme, cible, mult, null);
            double degats = avantCoup(p, bilan, arme, cible, f, null);
            if (degats <= 0) { e.setCancelled(true); return; }
            fixer(e, degats);
            apresCoup(p, bilan, cible, degats, !balayage);
            return;
        }

        if (source instanceof Projectile pr) {
            Tir t = tirs.get(pr.getUniqueId());
            Player p = t == null ? null : Bukkit.getPlayer(t.tireur());
            if (p != null) {
                Frappe f = calculer(p, t.bilan(), t.arme(), cible, t.multiplicateur(), t.magique());
                double degats = avantCoup(p, t.bilan(), t.arme(), cible, f, t.competence());
                if (degats <= 0) { e.setCancelled(true); return; }
                fixer(e, degats);
                apresCoup(p, t.bilan(), cible, degats, false);
                return;
            }
        }

        // un monstre frappe un joueur : sa force est le dégât du jeu de base, la défense du joueur s'applique
        if (cible instanceof Player victime && plugin.reglages().monstresContreJoueurs) {
            LivingEntity monstre = source instanceof LivingEntity l ? l
                : source instanceof Projectile pr && pr.getShooter() instanceof LivingEntity l2 ? l2 : null;
            if (monstre == null || monstre instanceof Player) return;
            // un joueur sans aucun objet de vorace-item garde les règles du jeu de base (armure en diamant...)
            if (!plugin.reglages().formulePartout && plugin.equipement().bilan(victime).portes().isEmpty()) return;
            coupDuJeu(e, monstre, victime, plugin.reglages().attaqueMonstres);
        }
    }

    /**
     * Un coup qui garde sa force du jeu de base (monstre, ou joueur sans arme de vorace-item) contre un joueur :
     * cette force sert de Puissance, la défense du joueur (Résistance + Armure de son équipement) s'applique.
     */
    private void coupDuJeu(EntityDamageByEntityEvent e, LivingEntity attaquant, Player victime, double multiplicateur) {
        Reglages r = plugin.reglages();
        Equipement.Bilan bilan = plugin.equipement().bilan(victime);
        double esquive = Math.min(r.esquiveMax, plugin.profil().esquive(victime) + bilan.total("dodge-rating"));
        if (ThreadLocalRandom.current().nextDouble() * 100 < esquive) {
            e.setCancelled(true);
            signalerEsquive(victime, attaquant instanceof Player p ? p : null);
            return;
        }
        double puissance = e.getDamage() * multiplicateur;
        double degats = Formule.degats(puissance, defense(victime, false));
        degats *= 1 - Math.min(80, Math.max(0, bilan.total("damage-reduction"))) / 100.0;
        degats = Formule.arrondir(degats);
        fixer(e, degats);
        userArmure(victime);
        double renvoi = degats * bilan.total("damage-reflect") / 100.0;
        if (renvoi >= 1 && !attaquant.isDead()) {
            direct = true;
            degatsDirects = Math.round(renvoi);
            try { attaquant.damage(degatsDirects, victime); } finally { direct = false; }
        }
    }

    /** Annonce le coup aux autres plugins et montre l'esquive ou le critique. Renvoie les dégâts à infliger (0 = rien). */
    private double avantCoup(Player attaquant, Equipement.Bilan bilan, ModeleObjet arme, LivingEntity cible, Frappe f, String competence) {
        if (f.esquive()) {
            signalerEsquive(cible instanceof Player v ? v : null, attaquant);
            return 0;
        }
        VoraceItemDegatsEvent ev = new VoraceItemDegatsEvent(attaquant, cible, f.degats(), f.magique(), f.critique(),
            arme == null ? null : arme.typeAttaque(), arme == null ? null : arme.element(), competence);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled() || ev.getDegats() <= 0) return 0;
        if (f.critique()) {
            cible.getWorld().spawnParticle(Particle.CRIT, cible.getLocation().add(0, cible.getHeight() * 0.6, 0), 12, 0.3, 0.3, 0.3, 0.2);
            attaquant.playSound(attaquant.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.1f);
        }
        if (plugin.reglages().chiffres) {
            attaquant.sendActionBar(Textes.objet((f.critique() ? "<#ffd166><bold>" : "<#ff8a8a>") + Textes.nombre(ev.getDegats())
                + (f.critique() ? " critique" : "") + (f.brut() ? " <white>(brut)" : "")));
        }
        return ev.getDegats();
    }

    /** Vol de vie, vol de mana, renvoi de dégâts et usure après un coup qui a porté. */
    private void apresCoup(Player attaquant, Equipement.Bilan bilan, LivingEntity cible, double degats, boolean userArme) {
        double volVie = degats * bilan.total("lifesteal") / 100.0;
        if (volVie > 0 && !attaquant.isDead()) {
            AttributeInstance max = attaquant.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) attaquant.setHealth(Math.min(max.getValue(), attaquant.getHealth() + volVie));
        }
        double volMana = degats * bilan.total("manasteal") / 100.0;
        if (volMana > 0) plugin.ressources().rendreMana(attaquant, volMana);
        if (userArme && bilan.arme() != null) plugin.usure().user(attaquant, EquipmentSlot.HAND, plugin.reglages().usureParCoup, true);
        if (cible instanceof Player victime) {
            userArmure(victime);
            double renvoi = degats * plugin.equipement().bilan(victime).total("damage-reflect") / 100.0;
            if (renvoi >= 1 && !attaquant.isDead()) {
                direct = true;
                degatsDirects = Math.round(renvoi);
                try { attaquant.damage(degatsDirects, victime); } finally { direct = false; }
            }
        }
    }

    private void userArmure(Player victime) {
        int points = plugin.reglages().usureParCoup;
        for (EquipmentSlot place : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFF_HAND}) {
            plugin.usure().user(victime, place, points, true);
        }
    }

    private void signalerEsquive(Player victime, Player attaquant) {
        if (victime != null) {
            victime.sendActionBar(Textes.objet("<#7dffb4>Esquive"));
            victime.playSound(victime.getLocation(), Sound.ENTITY_PLAYER_ATTACK_WEAK, 0.7f, 1.6f);
        }
        if (attaquant != null) attaquant.sendActionBar(Textes.objet("<#a8a8a8>Esquivé"));
    }

    /** Remplace les dégâts de l'événement : les réductions d'armure du jeu de base sont retirées (si le réglage le demande). */
    @SuppressWarnings("deprecation")
    private void fixer(EntityDamageByEntityEvent e, double degats) {
        double avant = e.getDamage(EntityDamageEvent.DamageModifier.BASE);
        if (plugin.reglages().ignorerArmureVanilla) {
            for (EntityDamageEvent.DamageModifier m : new EntityDamageEvent.DamageModifier[]{EntityDamageEvent.DamageModifier.ARMOR,
                EntityDamageEvent.DamageModifier.RESISTANCE, EntityDamageEvent.DamageModifier.MAGIC,
                EntityDamageEvent.DamageModifier.HARD_HAT, EntityDamageEvent.DamageModifier.INVULNERABILITY_REDUCTION}) {
                if (e.isApplicable(m)) e.setDamage(m, 0);
            }
            // le blocage au bouclier garde sa part ; les cœurs d'absorption encaissent d'abord
            double reste = degats;
            if (e.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)) {
                double bloque = avant <= 0 ? 0 : e.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) / avant * degats;
                e.setDamage(EntityDamageEvent.DamageModifier.BLOCKING, bloque);
                reste += bloque;
            }
            if (e.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION) && e.getEntity() instanceof LivingEntity l) {
                e.setDamage(EntityDamageEvent.DamageModifier.ABSORPTION, -Math.min(Math.max(0, reste), l.getAbsorptionAmount()));
            }
            e.setDamage(EntityDamageEvent.DamageModifier.BASE, degats);
        } else {
            e.setDamage(degats);
        }
    }

    /** Part des dégâts selon le temps depuis le coup précédent (recharge de l'arme). */
    private double charge(Player p) {
        int maintenant = Bukkit.getCurrentTick();
        Integer avant = dernierCoup.put(p.getUniqueId(), maintenant);
        if (avant == null || maintenant < avant) return 1;
        AttributeInstance vitesse = p.getAttribute(Attribute.ATTACK_SPEED);
        return Formule.charge(maintenant - avant, vitesse == null ? 4 : vitesse.getValue());
    }

    // ------------------------------------------------------------------ tirs

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void tir(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !plugin.reglages().combat) return;
        Equipement.Bilan bilan = plugin.equipement().bilan(p);
        if (bilan.arme() == null || !(e.getProjectile() instanceof Projectile pr)) return;
        tirs.put(pr.getUniqueId(), new Tir(p.getUniqueId(), bilan, bilan.arme().modele(), 0.2 + 0.8 * Math.min(1, e.getForce()), null, null));
        plugin.usure().user(p, EquipmentSlot.HAND, plugin.reglages().usureParCoup, true);
    }

    /** Projectile lancé par une compétence : il portera un coup calculé avec ce multiplicateur. */
    public void suivre(Projectile pr, Player tireur, double multiplicateur, Boolean magique, String competence) {
        Equipement.Bilan bilan = plugin.equipement().bilan(tireur);
        tirs.put(pr.getUniqueId(), new Tir(tireur.getUniqueId(), bilan, bilan.arme() == null ? null : bilan.arme().modele(), multiplicateur, magique, competence));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void impact(ProjectileHitEvent e) {
        UUID id = e.getEntity().getUniqueId();
        if (tirs.containsKey(id)) Bukkit.getScheduler().runTaskLater(plugin, () -> tirs.remove(id), 2L);
    }

    // ------------------------------------------------------------------ mort, départ

    @EventHandler(priority = EventPriority.HIGH)
    public void mort(PlayerDeathEvent e) {
        double part = plugin.reglages().perteMort;
        if (part <= 0) return;
        Player p = e.getEntity();
        if (e.getKeepInventory()) {
            ItemStack[] contenu = p.getInventory().getContents();
            for (int i = 0; i < contenu.length; i++) {
                if (contenu[i] != null && plugin.usure().perdre(contenu[i], part)) p.getInventory().setItem(i, contenu[i]);
            }
        } else {
            for (ItemStack objet : e.getDrops()) plugin.usure().perdre(objet, part);
        }
        plugin.equipement().oublier(p);
    }

    @EventHandler
    public void depart(PlayerQuitEvent e) {
        dernierCoup.remove(e.getPlayer().getUniqueId());
    }
}

package fr.vorace.item.competences;

import fr.vorace.item.Textes;
import fr.vorace.item.VoraceItem;
import fr.vorace.item.api.RessourcesJoueur;
import fr.vorace.item.api.VoraceItemCompetenceEvent;
import fr.vorace.item.combat.Equipement;
import fr.vorace.item.definitions.ModeleObjet;
import fr.vorace.item.exemplaire.Exemplaire;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Compétences d'armes (competences.yml) : un clic avec l'arme lance la compétence, qui coûte du mana ou de la stamina
 * et a un temps de recharge, affiché sur l'objet comme celui d'une perle de l'Ender.
 * <p>
 * Une compétence est une suite d'effets simples : degats, projectile, ruee, saut, soin, effet (de potion), son,
 * particules, message, commande. Les compétences des classes, plus riches, viendront du plugin des classes.
 */
public final class Competences implements Listener {

    /** @param recharge en secondes */
    public record Competence(String id, String nom, List<String> description, double recharge, double mana, double stamina,
                             List<Map<String, Object>> effets) {}

    public enum Lancer { FAIT, INCONNUE, RECHARGE, RESSOURCES, ANNULE }

    public static final List<String> TYPES = List.of("degats", "projectile", "ruee", "saut", "soin", "effet", "son", "particules", "message", "commande");

    private final VoraceItem plugin;
    private volatile Map<String, Competence> competences = Map.of();
    private final Map<String, Long> recharges = new HashMap<>();
    private final Map<UUID, Long> derniersClics = new HashMap<>();

    public Competences(VoraceItem plugin) {
        this.plugin = plugin;
    }

    public void charger(Path fichier, List<String> problemes) {
        Map<String, Competence> lues = new LinkedHashMap<>();
        if (Files.isRegularFile(fichier)) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier.toFile());
            for (String id : y.getKeys(false)) {
                ConfigurationSection s = y.getConfigurationSection(id);
                if (s == null) continue;
                List<Map<String, Object>> effets = new ArrayList<>();
                for (Map<?, ?> brut : s.getMapList("effets")) {
                    Map<String, Object> e = new LinkedHashMap<>();
                    brut.forEach((k, v) -> e.put(Textes.cle(String.valueOf(k)).replace('_', '-'), v));
                    String type = Textes.cle(String.valueOf(e.getOrDefault("type", "")));
                    if (!TYPES.contains(type)) {
                        problemes.add("Compétence " + id + " : effet inconnu « " + e.get("type") + " » " + TYPES);
                        continue;
                    }
                    e.put("type", type);
                    effets.add(e);
                }
                String propre = id.toLowerCase(Locale.ROOT);
                lues.put(propre, new Competence(propre, s.getString("nom", Textes.majuscule(propre.replace('_', ' '))), s.getStringList("description"),
                    Math.max(0, s.getDouble("recharge", 0)), Math.max(0, s.getDouble("mana", 0)), Math.max(0, s.getDouble("stamina", 0)), effets));
            }
        }
        competences = lues;
    }

    public Map<String, Competence> toutes() { return competences; }
    public Competence parId(String id) { return id == null ? null : competences.get(id.toLowerCase(Locale.ROOT)); }

    public double recharge(String id) {
        Competence c = parId(id);
        return c == null ? 0 : c.recharge();
    }

    /** Texte affiché sur l'objet : « Entaille (6 s · 15 SP) », ou null si la compétence n'existe pas. */
    public String ligne(String id) {
        Competence c = parId(id);
        if (c == null) return null;
        List<String> cout = new ArrayList<>();
        if (c.recharge() > 0) cout.add(Textes.nombre(c.recharge()) + " s");
        if (c.mana() > 0) cout.add(Textes.nombre(c.mana()) + " MP");
        if (c.stamina() > 0) cout.add(Textes.nombre(c.stamina()) + " SP");
        return "<#ffd166>" + c.nom() + (cout.isEmpty() ? "" : " <#6f6f6f>(" + String.join(" · ", cout) + ")");
    }

    // ------------------------------------------------------------------ déclenchement

    @EventHandler(priority = EventPriority.HIGH)
    @SuppressWarnings("deprecation") // Material#isInteractable : suffisant pour reconnaître coffres, portes et établis
    public void clic(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        boolean droit = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        boolean gauche = a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK;
        if (!droit && !gauche) return;
        Player p = e.getPlayer();
        ItemStack objet = e.getItem();
        Exemplaire ex = plugin.exemplaires().lire(objet);
        if (ex == null) return;
        ModeleObjet m = plugin.catalogue().modele(ex.modele);
        if (m == null || m.competences().isEmpty()) return;
        String cle = droit ? "clic-droit" : "clic-gauche";
        String id = p.isSneaking() && m.competences().containsKey("accroupi-" + cle) ? m.competences().get("accroupi-" + cle) : m.competences().get(cle);
        if (id == null) return;
        // un clic droit sur un bloc qui s'utilise (coffre, porte, établi...) ne lance pas la compétence
        if (a == Action.RIGHT_CLICK_BLOCK && !p.isSneaking() && e.getClickedBlock() != null && e.getClickedBlock().getType().isInteractable()) return;
        // un même clic sur un bloc arrive parfois deux fois (bloc, puis objet) : le second est ignoré
        long maintenant = System.currentTimeMillis();
        Long dernier = derniersClics.put(p.getUniqueId(), maintenant);
        if (dernier != null && maintenant - dernier < 150) return;
        if (ex.casse()) { p.sendActionBar(Textes.objet("<#ff5f5f>Cet objet est cassé")); return; }
        if (!plugin.equipement().utilisable(p, m)) { p.sendActionBar(Textes.objet("<#ff5f5f>Tu ne remplis pas les conditions de cet objet")); return; }
        lancer(p, id, objet, true);
    }

    @EventHandler
    public void depart(PlayerQuitEvent e) {
        String debut = e.getPlayer().getUniqueId() + ":";
        recharges.keySet().removeIf(k -> k.startsWith(debut));
        derniersClics.remove(e.getPlayer().getUniqueId());
    }

    /**
     * Lance une compétence.
     *
     * @param objet     l'objet qui la porte (son temps de recharge s'affiche dessus), ou null
     * @param verifier  vrai pour vérifier la recharge et dépenser mana et stamina ; faux pour la lancer sans condition
     */
    public Lancer lancer(Player p, String id, ItemStack objet, boolean verifier) {
        Competence c = parId(id);
        if (c == null) return Lancer.INCONNUE;
        String cle = p.getUniqueId() + ":" + c.id();
        long maintenant = System.currentTimeMillis();
        Equipement.Bilan bilan = plugin.equipement().bilan(p);
        if (verifier) {
            Long fin = recharges.get(cle);
            if (fin != null && fin > maintenant) {
                p.sendActionBar(Textes.objet("<#a8a8a8>" + c.nom() + " : encore <white>" + Textes.nombre(Math.ceil((fin - maintenant) / 100.0) / 10.0) + " s"));
                return Lancer.RECHARGE;
            }
        }
        VoraceItemCompetenceEvent ev = new VoraceItemCompetenceEvent(p, c.id(), objet);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) return Lancer.ANNULE;
        if (verifier) {
            RessourcesJoueur res = plugin.ressources();
            if (!res.depenser(p, c.mana(), c.stamina())) {
                p.sendActionBar(Textes.objet(res.mana(p) < c.mana() ? "<#6fb7ff>Pas assez de mana" : "<#ffd166>Pas assez de stamina"));
                return Lancer.RESSOURCES;
            }
            double recharge = c.recharge() * (1 - Math.min(75, Math.max(0, bilan.total("cooldown-reduction"))) / 100.0);
            if (recharge > 0) {
                recharges.put(cle, maintenant + (long) (recharge * 1000));
                if (objet != null && objet.hasItemMeta() && objet.getItemMeta().hasUseCooldown()) p.setCooldown(objet, (int) Math.round(recharge * 20));
            }
        }
        executer(p, c);
        return Lancer.FAIT;
    }

    // ------------------------------------------------------------------ effets

    private void executer(Player p, Competence c) {
        List<LivingEntity> touches = new ArrayList<>();
        for (Map<String, Object> e : c.effets()) {
            try {
                effet(p, c, e, touches);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Compétence " + c.id() + ", effet « " + e.get("type") + " » : " + ex);
            }
        }
    }

    private void effet(Player p, Competence c, Map<String, Object> e, List<LivingEntity> touches) {
        switch (String.valueOf(e.get("type"))) {
            case "degats" -> {
                double mult = nombre(e, "multiplicateur", 1);
                Boolean magique = nature(e);
                int max = (int) nombre(e, "cibles-max", 8);
                List<LivingEntity> cibles = cibles(p, e);
                touches.clear();
                for (LivingEntity cible : cibles) {
                    if (touches.size() >= max) break;
                    if (plugin.combat().frapper(p, cible, mult, magique, c.id()) > 0) touches.add(cible);
                }
            }
            case "projectile" -> {
                boolean magique = Boolean.TRUE.equals(nature(e));
                Projectile pr = magique || "boule".equals(texte(e, "forme", "")) ? p.launchProjectile(Snowball.class) : p.launchProjectile(Arrow.class);
                pr.setVelocity(p.getEyeLocation().getDirection().multiply(nombre(e, "vitesse", 2.0)));
                if (pr instanceof Arrow fleche) fleche.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                if (e.containsKey("gravite")) pr.setGravity(Boolean.parseBoolean(String.valueOf(e.get("gravite"))));
                plugin.combat().suivre(pr, p, nombre(e, "multiplicateur", 1), nature(e), c.id());
            }
            case "ruee" -> {
                Vector d = p.getLocation().getDirection();
                if (!Boolean.parseBoolean(texte(e, "vertical", "false"))) d.setY(0);
                if (d.lengthSquared() > 1e-6) p.setVelocity(d.normalize().multiply(nombre(e, "force", 1.2)).setY(Math.max(d.getY(), nombre(e, "hauteur", 0.15))));
            }
            case "saut" -> p.setVelocity(p.getVelocity().setY(nombre(e, "force", 0.8)));
            case "soin" -> {
                AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                if (max != null) {
                    double soin = nombre(e, "valeur", 0) + max.getValue() * nombre(e, "pourcent", 0) / 100.0;
                    soin *= 1 + plugin.equipement().bilan(p).total("healing-power") / 100.0;
                    p.setHealth(Math.min(max.getValue(), p.getHealth() + Math.max(0, soin)));
                }
            }
            case "effet" -> {
                PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(texte(e, "potion", "speed").toLowerCase(Locale.ROOT).replace("minecraft:", "")));
                if (type == null) throw new IllegalArgumentException("effet de potion inconnu « " + e.get("potion") + " »");
                PotionEffect effet = new PotionEffect(type, (int) Math.round(nombre(e, "duree", 5) * 20), Math.max(0, (int) nombre(e, "niveau", 1) - 1), false, true, true);
                if ("cibles".equals(texte(e, "sur", "soi"))) for (LivingEntity l : touches) l.addPotionEffect(effet);
                else p.addPotionEffect(effet);
            }
            case "son" -> {
                String nom = texte(e, "son", "entity.player.attack.sweep").toLowerCase(Locale.ROOT);
                p.getWorld().playSound(p.getLocation(), nom, (float) nombre(e, "volume", 1), (float) nombre(e, "hauteur", 1));
            }
            case "particules" -> {
                Particle particule;
                try {
                    particule = Particle.valueOf(texte(e, "particule", "SWEEP_ATTACK").toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""));
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("particule inconnue « " + e.get("particule") + " »");
                }
                if (particule.getDataType() != Void.class) throw new IllegalArgumentException("la particule « " + e.get("particule") + " » demande des données, elle n'est pas prise en charge");
                double distance = nombre(e, "distance", 1.5);
                double etendue = nombre(e, "etendue", 0.4);
                int nombre = (int) nombre(e, "nombre", 8);
                if ("cibles".equals(texte(e, "sur", "soi"))) {
                    for (LivingEntity l : touches) l.getWorld().spawnParticle(particule, l.getLocation().add(0, l.getHeight() / 2, 0), nombre, etendue, etendue, etendue, 0.02);
                } else {
                    Location ou = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(distance));
                    p.getWorld().spawnParticle(particule, ou, nombre, etendue, etendue, etendue, 0.02);
                }
            }
            case "message" -> p.sendMessage(Textes.objet(texte(e, "texte", "")));
            case "commande" -> fr.vorace.objets.EcouteurObjets.executer(p, texte(e, "commande", ""));
            default -> { }
        }
    }

    /** Cibles d'un effet de dégâts : « visee » (la créature regardée), « zone » (autour du joueur) ou « cone » (devant lui). */
    private List<LivingEntity> cibles(Player p, Map<String, Object> e) {
        String mode = Textes.cle(texte(e, "cible", "visee"));
        double portee = nombre(e, "portee", 3.5);
        List<LivingEntity> l = new ArrayList<>();
        if (mode.equals("visee")) {
            RayTraceResult r = p.getWorld().rayTrace(p.getEyeLocation(), p.getEyeLocation().getDirection(), portee, FluidCollisionMode.NEVER, true,
                nombre(e, "largeur", 0.3), x -> x instanceof LivingEntity && !x.equals(p) && !x.isDead());
            if (r != null && r.getHitEntity() instanceof LivingEntity cible) l.add(cible);
            return l;
        }
        double rayon = nombre(e, "rayon", portee);
        double cos = Math.cos(Math.toRadians(nombre(e, "angle", 90) / 2));
        Vector regard = p.getLocation().getDirection().setY(0);
        if (regard.lengthSquared() > 1e-6) regard.normalize();
        for (Entity x : p.getNearbyEntities(rayon, rayon, rayon)) {
            if (!(x instanceof LivingEntity cible) || x.isDead() || x.equals(p)) continue;
            if (x instanceof org.bukkit.entity.ArmorStand) continue;
            Vector vers = x.getLocation().toVector().subtract(p.getLocation().toVector());
            if (vers.lengthSquared() > rayon * rayon) continue;
            if (mode.equals("cone")) {
                vers.setY(0);
                if (vers.lengthSquared() > 1e-6 && vers.normalize().dot(regard) < cos) continue;
            }
            l.add(cible);
        }
        l.sort((a, b) -> Double.compare(a.getLocation().distanceSquared(p.getLocation()), b.getLocation().distanceSquared(p.getLocation())));
        return l;
    }

    private static Boolean nature(Map<String, Object> e) {
        Object n = e.get("nature");
        if (n == null) return null;
        return Textes.cle(String.valueOf(n)).startsWith("mag");
    }

    private static double nombre(Map<String, Object> e, String cle, double defaut) {
        Object v = e.get(cle);
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) return defaut;
        try {
            return Double.parseDouble(String.valueOf(v).replace(',', '.').replace("%", "").trim());
        } catch (NumberFormatException ex) {
            return defaut;
        }
    }

    private static String texte(Map<String, Object> e, String cle, String defaut) {
        Object v = e.get(cle);
        return v == null ? defaut : String.valueOf(v);
    }
}

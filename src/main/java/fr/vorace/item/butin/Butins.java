package fr.vorace.item.butin;

import fr.vorace.api.VoraceAPI;
import fr.vorace.item.Textes;
import fr.vorace.item.VoraceItem;
import fr.vorace.item.api.VoraceItemCreeEvent;
import fr.vorace.item.definitions.Catalogue;
import fr.vorace.item.definitions.Materiau.Piece;
import fr.vorace.item.definitions.ModeleObjet;
import fr.vorace.item.definitions.Rarete;
import fr.vorace.item.exemplaire.Exemplaires.Signature;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Butin des monstres (butins.yml). Chaque table a une chance de lâcher un objet, puis tire une entrée : une arme
 * modulaire assemblée avec les pièces des matériaux de l'étage, un modèle précis, ou un objet simple. Les monstres de
 * vorace-mob sont reconnus par leur identifiant (« vorace:kobold »), les autres par leur type (« zombie »).
 */
public final class Butins implements Listener {

    /**
     * Une entrée de table.
     *
     * @param kit       arme modulaire à assembler (ou null)
     * @param materiaux matériaux dont viennent les pièces (vide = tous)
     * @param modele    modèle précis de vorace-item (ou null)
     * @param objet     objet simple : « IRON_NUGGET » ou « vorace:... » (ou null)
     */
    public record Entree(double poids, String kit, List<String> materiaux, String modele, String objet, int min, int max) {}

    public record Table(String id, double chance, Map<String, Double> rangs, List<Entree> entrees) {}

    private final VoraceItem plugin;
    private final Random hasard = new Random();
    private final NamespacedKey cleMob = NamespacedKey.fromString("vorace:mob");
    private volatile Map<String, Table> tables = Map.of();
    private volatile Map<String, String> mobs = Map.of();

    public Butins(VoraceItem plugin) {
        this.plugin = plugin;
    }

    public Map<String, Table> tables() { return tables; }

    public void charger(Path fichier, List<String> problemes) {
        Map<String, Table> lues = new LinkedHashMap<>();
        Map<String, String> liens = new LinkedHashMap<>();
        if (Files.isRegularFile(fichier)) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier.toFile());
            ConfigurationSection st = y.getConfigurationSection("tables");
            if (st != null) for (String id : st.getKeys(false)) {
                ConfigurationSection s = st.getConfigurationSection(id);
                if (s == null) continue;
                Map<String, Double> rangs = new LinkedHashMap<>();
                ConfigurationSection sr = s.getConfigurationSection("rangs");
                if (sr != null) for (String r : sr.getKeys(false)) {
                    Rarete rang = plugin.catalogue().raretes().parId(r);
                    if (rang == null) problemes.add("butins.yml, table " + id + " : rang inconnu « " + r + " »");
                    else rangs.put(rang.id(), Math.max(0, sr.getDouble(r)));
                }
                List<Entree> entrees = new ArrayList<>();
                for (Map<?, ?> brut : s.getMapList("entrees")) {
                    Map<String, Object> e = new LinkedHashMap<>();
                    brut.forEach((k, v) -> e.put(Textes.cle(String.valueOf(k)), v));
                    int[] q = quantite(e.get("quantite"));
                    List<String> materiaux = new ArrayList<>();
                    if (e.get("materiaux") instanceof List<?> l) for (Object o : l) materiaux.add(Textes.cle(String.valueOf(o)));
                    Entree entree = new Entree(e.get("poids") instanceof Number n ? n.doubleValue() : 1,
                        e.get("kit") == null ? null : String.valueOf(e.get("kit")).toLowerCase(Locale.ROOT), materiaux,
                        e.get("modele") == null ? null : String.valueOf(e.get("modele")).toLowerCase(Locale.ROOT),
                        e.get("objet") == null ? null : String.valueOf(e.get("objet")), q[0], q[1]);
                    if (entree.kit() == null && entree.modele() == null && entree.objet() == null) {
                        problemes.add("butins.yml, table " + id + " : une entrée n'a ni « kit », ni « modele », ni « objet »");
                        continue;
                    }
                    if (entree.kit() != null && (plugin.catalogue().modele(entree.kit()) == null || plugin.catalogue().modele(entree.kit()).kit() == null)) {
                        problemes.add("butins.yml, table " + id + " : « " + entree.kit() + " » n'est pas une arme modulaire");
                        continue;
                    }
                    if (entree.modele() != null && plugin.catalogue().modele(entree.modele()) == null) {
                        problemes.add("butins.yml, table " + id + " : modèle inconnu « " + entree.modele() + " »");
                        continue;
                    }
                    if (entree.poids() > 0) entrees.add(entree);
                }
                lues.put(id.toLowerCase(Locale.ROOT), new Table(id.toLowerCase(Locale.ROOT), Math.max(0, Math.min(1, s.getDouble("chance", 0.1))), rangs, entrees));
            }
            ConfigurationSection sm = y.getConfigurationSection("mobs");
            if (sm != null) for (String mob : sm.getKeys(false)) {
                String table = String.valueOf(sm.get(mob)).toLowerCase(Locale.ROOT);
                if (!lues.containsKey(table)) problemes.add("butins.yml : le mob « " + mob + " » renvoie à une table inconnue « " + table + " »");
                else liens.put(mob.toLowerCase(Locale.ROOT).replace("minecraft:", ""), table);
            }
        }
        tables = lues;
        mobs = liens;
    }

    private static int[] quantite(Object q) {
        if (q instanceof Number n) return new int[]{Math.max(1, n.intValue()), Math.max(1, n.intValue())};
        if (q != null) {
            String[] p = String.valueOf(q).trim().split("\\s*-\\s*");
            try {
                int a = Integer.parseInt(p[0]);
                int b = p.length > 1 ? Integer.parseInt(p[1]) : a;
                return new int[]{Math.max(1, Math.min(a, b)), Math.max(1, Math.max(a, b))};
            } catch (NumberFormatException ignore) { }
        }
        return new int[]{1, 1};
    }

    // ------------------------------------------------------------------ tirage

    /** Table d'un monstre : par son identifiant de vorace-mob (« vorace:kobold »), sinon par son type (« zombie »). */
    public Table table(LivingEntity monstre) {
        String id = cleMob == null ? null : monstre.getPersistentDataContainer().get(cleMob, PersistentDataType.STRING);
        String table = id == null ? null : mobs.get("vorace:" + id.toLowerCase(Locale.ROOT));
        if (table == null) table = mobs.get(monstre.getType().getKey().getKey());
        return table == null ? null : tables.get(table);
    }

    /**
     * Tire le butin d'une table.
     *
     * @param chance   chance du joueur (modificateur « luck ») : +1 % de chance de butin par point
     * @param garanti  vrai pour ignorer la chance de la table (essais)
     */
    public List<ItemStack> tirer(Table t, Player joueur, double chance, boolean garanti) {
        List<ItemStack> l = new ArrayList<>();
        if (t == null || t.entrees().isEmpty()) return l;
        if (!garanti && hasard.nextDouble() >= t.chance() * (1 + Math.max(0, chance) / 100.0)) return l;
        double total = 0;
        for (Entree e : t.entrees()) total += e.poids();
        double tire = hasard.nextDouble() * total;
        Entree choisie = t.entrees().get(t.entrees().size() - 1);
        for (Entree e : t.entrees()) {
            tire -= e.poids();
            if (tire < 0) { choisie = e; break; }
        }
        ItemStack objet = creer(t, choisie);
        if (objet == null) return l;
        String modele = plugin.exemplaires().modele(objet);
        if (modele != null) {
            VoraceItemCreeEvent ev = new VoraceItemCreeEvent(joueur, modele, objet, VoraceItemCreeEvent.Origine.BUTIN);
            Bukkit.getPluginManager().callEvent(ev);
            objet = ev.getObjet();
        }
        l.add(objet);
        return l;
    }

    private ItemStack creer(Table t, Entree e) {
        Catalogue c = plugin.catalogue();
        int quantite = e.min() + hasard.nextInt(e.max() - e.min() + 1);
        double position = hasard.nextDouble() * plugin.reglages().butinPositionMax;
        if (e.kit() != null) {
            ModeleObjet kit = c.modele(e.kit());
            if (kit == null || kit.kit() == null) return null;
            Map<String, String> pieces = new LinkedHashMap<>();
            for (String emplacement : kit.kit().emplacements()) {
                List<Piece> possibles = new ArrayList<>();
                for (Piece p : c.pieces(kit.id(), emplacement)) if (e.materiaux().isEmpty() || e.materiaux().contains(p.materiau())) possibles.add(p);
                if (possibles.isEmpty()) return null;
                pieces.put(emplacement, possibles.get(hasard.nextInt(possibles.size())).id());
            }
            Rarete rang = rang(t, kit.kit().rangMax());
            return plugin.exemplaires().creer(kit, rang, false, position, pieces, Signature.AUCUNE, 1);
        }
        if (e.modele() != null) {
            ModeleObjet m = c.modele(e.modele());
            return m == null ? null : plugin.exemplaires().creer(m, m.rang(), false, position, null, Signature.AUCUNE, quantite);
        }
        String o = e.objet().trim();
        if (o.toLowerCase(Locale.ROOT).startsWith("vorace:")) return VoraceAPI.objet(o.substring(7).toLowerCase(Locale.ROOT), quantite);
        Material m = Material.matchMaterial(o);
        return m == null || !m.isItem() || m.isAir() ? null : new ItemStack(m, Math.min(quantite, m.getMaxStackSize()));
    }

    /** Rang tiré selon les poids de la table (90 % Commune, 10 % Peu commune dans Babel), sans dépasser le rang maximal. */
    private Rarete rang(Table t, Rarete max) {
        Catalogue c = plugin.catalogue();
        double total = 0;
        for (Map.Entry<String, Double> e : t.rangs().entrySet()) {
            Rarete r = c.raretes().parId(e.getKey());
            if (r != null && r.ordre() <= max.ordre()) total += e.getValue();
        }
        if (total <= 0) return c.raretes().premier();
        double tire = hasard.nextDouble() * total;
        for (Map.Entry<String, Double> e : t.rangs().entrySet()) {
            Rarete r = c.raretes().parId(e.getKey());
            if (r == null || r.ordre() > max.ordre()) continue;
            tire -= e.getValue();
            if (tire < 0) return r;
        }
        return c.raretes().premier();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void mort(EntityDeathEvent e) {
        if (!plugin.reglages().butin || e.getEntity() instanceof Player) return;
        Player tueur = e.getEntity().getKiller();
        if (tueur == null) return;
        Table t = table(e.getEntity());
        if (t == null) return;
        e.getDrops().addAll(tirer(t, tueur, plugin.equipement().bilan(tueur).total("luck"), false));
    }
}

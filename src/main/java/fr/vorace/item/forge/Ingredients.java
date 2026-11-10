package fr.vorace.item.forge;

import fr.vorace.api.VoraceAPI;
import fr.vorace.item.Textes;
import fr.vorace.item.VoraceItem;
import fr.vorace.item.definitions.ModeleObjet;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Ce que demande une recette. Trois écritures :
 * <ul>
 *   <li>« IRON_INGOT » : un objet du jeu de base (ni un objet de vorace-core, ni un objet de vorace-item) ;</li>
 *   <li>« vorace:lingot_fer » : un objet de vorace-core ;</li>
 *   <li>« item:rubis_taille » : un objet de vorace-item (gemme, matériau).</li>
 * </ul>
 */
public final class Ingredients {

    private final VoraceItem plugin;

    public Ingredients(VoraceItem plugin) {
        this.plugin = plugin;
    }

    public boolean correspond(ItemStack objet, String ingredient) {
        if (objet == null || objet.getType().isAir()) return false;
        String i = ingredient.toLowerCase(Locale.ROOT);
        String modele = plugin.exemplaires().modele(objet);
        if (i.startsWith("item:")) return i.substring(5).equals(modele);
        String vorace = VoraceAPI.id(objet);
        if (i.startsWith("vorace:")) return i.substring(7).equals(vorace);
        Material m = Material.matchMaterial(i);
        return m != null && objet.getType() == m && modele == null && vorace == null;
    }

    public int compter(Player p, String ingredient) {
        int n = 0;
        for (ItemStack objet : p.getInventory().getStorageContents()) if (correspond(objet, ingredient)) n += objet.getAmount();
        return n;
    }

    public boolean possede(Player p, Map<String, Integer> cout) {
        for (Map.Entry<String, Integer> e : cout.entrySet()) if (compter(p, e.getKey()) < e.getValue()) return false;
        return true;
    }

    /** Retire les ingrédients de l'inventaire. Faux (et rien de retiré) s'il en manque. */
    public boolean retirer(Player p, Map<String, Integer> cout) {
        if (!possede(p, cout)) return false;
        ItemStack[] contenu = p.getInventory().getStorageContents();
        for (Map.Entry<String, Integer> e : cout.entrySet()) {
            int reste = e.getValue();
            for (int i = 0; i < contenu.length && reste > 0; i++) {
                ItemStack objet = contenu[i];
                if (!correspond(objet, e.getKey())) continue;
                int pris = Math.min(reste, objet.getAmount());
                reste -= pris;
                if (pris >= objet.getAmount()) contenu[i] = null;
                else objet.setAmount(objet.getAmount() - pris);
            }
        }
        p.getInventory().setStorageContents(contenu);
        return true;
    }

    /** Nom lisible d'un ingrédient. */
    public String nom(String ingredient) {
        String i = ingredient.toLowerCase(Locale.ROOT);
        if (i.startsWith("item:")) {
            ModeleObjet m = plugin.catalogue().modele(i.substring(5));
            return m == null ? i.substring(5) : m.nom();
        }
        if (i.startsWith("vorace:")) {
            ItemStack o = VoraceAPI.objet(i.substring(7), 1);
            if (o != null && o.hasItemMeta() && o.getItemMeta().hasDisplayName()) {
                return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(o.getItemMeta().displayName());
            }
            return i.substring(7);
        }
        Material m = Material.matchMaterial(i);
        return m == null ? ingredient : "<lang:" + m.translationKey() + ">";
    }

    /** Lignes de description d'un coût : « ✔ 2 × Lingot de fer (5) », en vert ou en rouge selon ce que possède le joueur. */
    public java.util.List<String> lignes(Player p, Map<String, Integer> cout) {
        java.util.List<String> l = new java.util.ArrayList<>();
        for (Map.Entry<String, Integer> e : cout.entrySet()) {
            int a = compter(p, e.getKey());
            boolean ok = a >= e.getValue();
            l.add((ok ? "<#7dffb4>✔ " : "<#ff5f7e>✘ ") + e.getValue() + " × " + nom(e.getKey()) + " <#8c8170>(" + a + ")");
        }
        return l;
    }

    /** Additionne des coûts, chaque quantité multipliée puis arrondie au plus proche (au moins 1). */
    public static Map<String, Integer> somme(Iterable<Map<String, Integer>> couts, double multiplicateur) {
        Map<String, Integer> total = new LinkedHashMap<>();
        for (Map<String, Integer> c : couts) c.forEach((k, v) -> total.merge(k, v, Integer::sum));
        if (multiplicateur != 1) total.replaceAll((k, v) -> Math.max(1, (int) Math.round(v * multiplicateur)));
        return total;
    }

    static String sansBalises(String s) {
        return Textes.echapper(s);
    }
}

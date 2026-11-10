package fr.vorace.item.exemplaire;

import java.lang.reflect.Method;
import org.bukkit.inventory.ItemStack;

/**
 * Écrit une clé à la racine de {@code minecraft:custom_data} (là où Bukkit ne laisse écrire que sous
 * « PublicBukkitValues »). Sert aux mods côté joueur qui lisent leurs propres clés, comme Armor Model API
 * ({@code armor_model_api}). Passe par le serveur (noms de Mojang, Paper 1.21.4) ; la clé est ensuite gardée par
 * Bukkit à côté de ses données quand l'objet est réécrit.
 */
public final class DonneesBrutes {
    private DonneesBrutes() {}

    /** Pose {@code cle: snbt} dans custom_data (snbt : un composé, « {a:"b"} ») ; renvoie la copie modifiée. */
    public static ItemStack poser(ItemStack objet, String cle, String snbt) throws ReflectiveOperationException {
        Class<?> craft = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
        Class<?> nmsObjet = Class.forName("net.minecraft.world.item.ItemStack");
        Class<?> type = Class.forName("net.minecraft.core.component.DataComponentType");
        Class<?> donnees = Class.forName("net.minecraft.world.item.component.CustomData");
        Class<?> compose = Class.forName("net.minecraft.nbt.CompoundTag");
        Class<?> tagCls = Class.forName("net.minecraft.nbt.Tag");
        Object nms = craft.getMethod("asNMSCopy", ItemStack.class).invoke(null, objet);
        Object custom = Class.forName("net.minecraft.core.component.DataComponents").getField("CUSTOM_DATA").get(null);
        Object courant = nmsObjet.getMethod("getOrDefault", type, Object.class).invoke(nms, custom, donnees.getField("EMPTY").get(null));
        Object tag = donnees.getMethod("copyTag").invoke(courant);
        Object valeur = Class.forName("net.minecraft.nbt.TagParser").getMethod("parseTag", String.class).invoke(null, snbt);
        compose.getMethod("put", String.class, tagCls).invoke(tag, cle, valeur);
        nmsObjet.getMethod("set", type, Object.class).invoke(nms, custom, donnees.getMethod("of", compose).invoke(null, tag));
        return (ItemStack) craft.getMethod("asBukkitCopy", nmsObjet).invoke(null, nms);
    }

    /** La valeur de la clé à la racine de custom_data (texte SNBT), ou null. */
    public static String lire(ItemStack objet, String cle) {
        try {
            Class<?> craft = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
            Class<?> nmsObjet = Class.forName("net.minecraft.world.item.ItemStack");
            Class<?> type = Class.forName("net.minecraft.core.component.DataComponentType");
            Class<?> donnees = Class.forName("net.minecraft.world.item.component.CustomData");
            Object nms = craft.getMethod("asNMSCopy", ItemStack.class).invoke(null, objet);
            Object custom = Class.forName("net.minecraft.core.component.DataComponents").getField("CUSTOM_DATA").get(null);
            Object courant = nmsObjet.getMethod("getOrDefault", type, Object.class).invoke(nms, custom, donnees.getField("EMPTY").get(null));
            Object tag = donnees.getMethod("copyTag").invoke(courant);
            Method get = tag.getClass().getMethod("get", String.class);
            Object v = get.invoke(tag, cle);
            return v == null ? null : v.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}

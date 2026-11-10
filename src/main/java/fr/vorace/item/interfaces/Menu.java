package fr.vorace.item.interfaces;

import fr.vorace.item.Textes;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Un menu de vorace-item : un coffre dont les cases sont des boutons, avec en plus des cases « libres » où le joueur
 * pose ses objets (forge, atelier). Les objets laissés dans les cases libres lui sont rendus à la fermeture.
 */
public final class Menu implements InventoryHolder {

    /** Ce que fait un bouton. droit = clic droit, maj = clic avec Maj. */
    @FunctionalInterface
    public interface Action {
        void clic(Player p, boolean droit, boolean maj);
    }

    public static final String TEXTE = "<#d9c9a8>";
    public static final String DISCRET = "<#8c8170>";
    public static final String TITRE = "<gradient:#f6b73c:#e8552d>";
    public static final String OUI = "<#7dffb4>";
    public static final String NON = "<#ff5f7e>";

    private final Inventory inventaire;
    private final Map<Integer, Action> actions = new HashMap<>();
    private final Set<Integer> libres = new HashSet<>();
    private Runnable changement;

    public Menu(String titre, int lignes) {
        this.inventaire = Bukkit.createInventory(this, Math.max(1, Math.min(6, lignes)) * 9, Textes.objet(TITRE + titre + "</gradient>"));
    }

    @Override
    public Inventory getInventory() { return inventaire; }

    public int taille() { return inventaire.getSize(); }

    public Menu bouton(int case_, ItemStack icone, Action action) {
        if (case_ < 0 || case_ >= taille()) return this;
        inventaire.setItem(case_, icone);
        if (action != null) actions.put(case_, action); else actions.remove(case_);
        return this;
    }

    /** Case où le joueur peut poser et reprendre un objet. */
    public Menu libre(int case_) {
        libres.add(case_);
        return this;
    }

    public boolean estLibre(int case_) { return libres.contains(case_); }
    public Set<Integer> libres() { return libres; }

    /** Appelé un tic après chaque changement dans une case libre (pour refaire l'aperçu). */
    public Menu quandChange(Runnable r) {
        this.changement = r;
        return this;
    }

    void change() {
        if (changement != null) changement.run();
    }

    /** Remplit les cases vides (hors cases libres) avec une vitre sans nom. */
    public Menu fond(String materiau) {
        ItemStack vitre = icone(materiau, " ");
        for (int i = 0; i < taille(); i++) if (!libres.contains(i) && inventaire.getItem(i) == null) inventaire.setItem(i, vitre);
        return this;
    }

    public void ouvrir(Player p) {
        p.openInventory(inventaire);
    }

    void clic(Player p, int case_, boolean droit, boolean maj) {
        Action a = actions.get(case_);
        if (a == null) return;
        try {
            a.clic(p, droit, maj);
        } catch (RuntimeException e) {
            p.sendMessage(Textes.message("<red>Erreur : " + Textes.echapper(String.valueOf(e.getMessage()))));
            Bukkit.getLogger().warning("[VoraceItem] menu : " + e);
        }
    }

    // ------------------------------------------------------------------ icônes

    public static Material materiau(String nom) {
        Material m = Material.matchMaterial(nom);
        return m == null || m.isAir() || !m.isItem() ? Material.PAPER : m;
    }

    /** Icône : nom (MiniMessage) et lignes de description (« » = ligne vide). */
    public static ItemStack icone(String materiau, String nom, String... lignes) {
        return icone(new ItemStack(materiau(materiau)), nom, false, lignes);
    }

    /**
     * Fait une icône d'un objet existant (copie).
     *
     * @param garder vrai pour garder le nom et la description de l'objet et ajouter les lignes à la suite
     */
    public static ItemStack icone(ItemStack base, String nom, boolean garder, String... lignes) {
        ItemStack it = base.clone();
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        if (nom != null) meta.displayName(Textes.objet(nom));
        List<Component> lore = new ArrayList<>();
        if (garder && meta.hasLore() && meta.lore() != null) lore.addAll(meta.lore());
        for (String l : lignes) lore.add(l.isEmpty() ? Component.empty() : Textes.objet(l.startsWith("<") ? l : TEXTE + l));
        meta.lore(lore.isEmpty() ? null : lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        it.setItemMeta(meta);
        return it;
    }

    /** Ligne de description d'un clic : « ▶ Clic » forger ». */
    public static String clic(String quoi, String effet) {
        return "<#f6b73c>▶ " + quoi + " " + DISCRET + "» <white>" + effet;
    }
}

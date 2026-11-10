package fr.vorace.item.interfaces;

import fr.vorace.item.VoraceItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Clics dans les menus de vorace-item : les boutons sont protégés, les cases libres se remplissent normalement. */
public final class EcouteurMenus implements Listener {

    private final VoraceItem plugin;

    public EcouteurMenus(VoraceItem plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void clic(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu menu)) return;
        if (!(e.getWhoClicked() instanceof Player p)) { e.setCancelled(true); return; }
        int brut = e.getRawSlot();
        boolean haut = brut >= 0 && brut < e.getInventory().getSize();
        if (haut) {
            if (menu.estLibre(brut)) {
                // le double-clic ramasserait aussi les icônes du menu
                if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) e.setCancelled(true);
                else prevenir(menu);
                return;
            }
            e.setCancelled(true);
            menu.clic(p, brut, e.isRightClick(), e.isShiftClick());
            return;
        }
        // inventaire du joueur : tout est permis sauf ce qui enverrait un objet sur un bouton
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) { e.setCancelled(true); return; }
        if (e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT) {
            e.setCancelled(true);
            ItemStack objet = e.getCurrentItem();
            if (objet == null || objet.getType().isAir()) return;
            Inventory inv = e.getInventory();
            for (int libre : menu.libres().stream().sorted().toList()) {
                ItemStack present = inv.getItem(libre);
                if (present == null || present.getType().isAir()) {
                    inv.setItem(libre, objet.clone());
                    e.setCurrentItem(null);
                    prevenir(menu);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void glisser(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu menu)) return;
        int taille = e.getInventory().getSize();
        for (int brut : e.getRawSlots()) {
            if (brut < taille && !menu.estLibre(brut)) { e.setCancelled(true); return; }
        }
        prevenir(menu);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void fermeture(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu menu) || !(e.getPlayer() instanceof Player p)) return;
        for (int libre : menu.libres()) {
            ItemStack objet = e.getInventory().getItem(libre);
            if (objet == null || objet.getType().isAir()) continue;
            e.getInventory().setItem(libre, null);
            rendre(p, objet);
        }
    }

    /** Donne un objet au joueur ; ce qui ne rentre pas dans son inventaire tombe à ses pieds. */
    public static void rendre(Player p, ItemStack objet) {
        Map<Integer, ItemStack> reste = p.getInventory().addItem(objet);
        for (ItemStack r : reste.values()) p.getWorld().dropItemNaturally(p.getLocation(), r);
    }

    private void prevenir(Menu menu) {
        Bukkit.getScheduler().runTask(plugin, menu::change);
    }
}

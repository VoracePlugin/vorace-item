package fr.vorace.item;

import fr.vorace.api.VoraceObjetMisAJourEvent;
import fr.vorace.item.exemplaire.Exemplaire;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Garde les objets de vorace-item cohérents : l'usure, la réparation et les enchantements du jeu de base ne les
 * touchent pas (ils ont leur propre durabilité et leur propre atelier), leur affichage est refait à la connexion et
 * quand vorace-core met à jour l'objet qui leur sert d'apparence.
 */
public final class EcouteurObjets implements Listener {

    private final VoraceItem plugin;

    public EcouteurObjets(VoraceItem plugin) {
        this.plugin = plugin;
    }

    private boolean estNotre(ItemStack objet) {
        return plugin.exemplaires().modele(objet) != null;
    }

    // ------------------------------------------------------------------ le jeu de base ne touche pas à nos objets

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void usure(PlayerItemDamageEvent e) {
        if (estNotre(e.getItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void raccommodage(PlayerItemMendEvent e) {
        if (estNotre(e.getItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void enclume(PrepareAnvilEvent e) {
        for (ItemStack i : e.getInventory().getContents()) if (estNotre(i)) { e.setResult(null); return; }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void meule(PrepareGrindstoneEvent e) {
        for (ItemStack i : e.getInventory().getContents()) if (estNotre(i)) { e.setResult(null); return; }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void forgeDuJeu(PrepareSmithingEvent e) {
        for (ItemStack i : e.getInventory().getContents()) if (estNotre(i)) { e.setResult(null); return; }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void etabli(PrepareItemCraftEvent e) {
        for (ItemStack i : e.getInventory().getMatrix()) if (estNotre(i)) { e.getInventory().setResult(null); return; }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void enchantement(PrepareItemEnchantEvent e) {
        if (estNotre(e.getItem())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ affichage à jour

    @EventHandler(priority = EventPriority.MONITOR)
    public void majVorace(VoraceObjetMisAJourEvent e) {
        Exemplaire ex = plugin.exemplaires().lire(e.getAncien());
        if (ex == null) return;
        ItemStack neuf = e.getNouveau();
        plugin.exemplaires().ecrire(neuf, ex);
        e.setNouveau(neuf);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void arrivee(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // un tic plus tard : après la mise à jour des objets par vorace-core
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) plugin.rafraichir(p); }, 2L);
    }

    @EventHandler
    public void depart(PlayerQuitEvent e) {
        plugin.depart(e.getPlayer());
    }

    // ------------------------------------------------------------------ l'équipement a changé

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void main(PlayerItemHeldEvent e) { plugin.equipementChange(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void echange(PlayerSwapHandItemsEvent e) { plugin.equipementChange(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void inventaire(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) plugin.equipementChange(p);
    }
}

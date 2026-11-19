package fr.vorace.item;

import fr.vorace.api.VoraceObjetMisAJourEvent;
import fr.vorace.item.exemplaire.Exemplaire;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Garde les objets de vorace-item cohérents : la réparation et les enchantements du jeu de base ne les touchent pas,
 * l'usure du jeu est remplacée par la leur (durabilité de vorace-item), une part de leur durabilité est perdue à la
 * mort, et leur affichage est refait à la connexion et quand vorace-core met à jour l'objet qui leur sert d'apparence.
 */
public final class EcouteurObjets implements Listener {

    private final VoraceItem plugin;

    public EcouteurObjets(VoraceItem plugin) {
        this.plugin = plugin;
    }

    private static final EquipmentSlot[] PLACES = {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD,
        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private boolean estNotre(ItemStack objet) {
        return plugin.exemplaires().modele(objet) != null;
    }

    // ------------------------------------------------------------------ le jeu de base ne touche pas à nos objets

    /**
     * Le jeu veut user l'objet (coup porté avec l'arme, coup reçu par une pièce d'armure, flèche tirée...) : il n'y
     * touche pas, l'objet perd à la place sa durabilité de vorace-item (« durabilite.usure-par-utilisation »).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void usure(PlayerItemDamageEvent e) {
        if (!estNotre(e.getItem())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        for (EquipmentSlot place : PLACES) {
            if (e.getItem().equals(p.getInventory().getItem(place))) {
                plugin.usure().user(p, place, plugin.reglages().usureParUtilisation, true);
                return;
            }
        }
    }

    /** Mort du joueur : ses objets perdent une part de leur durabilité restante (« durabilite.perte-a-la-mort »). */
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

    // Les autres machines du jeu : nos objets reposent sur des objets du jeu (épée, lingot...) et ne doivent servir ni
    // de minerai à fondre, ni de combustible, ni d'ingrédient, ni de paiement (fourneaux, alambic, tailleur de pierre,
    // métier à tisser, table de cartographie, balise, marchand, fabricateur, composteur, feu de camp).
    private static final java.util.Set<org.bukkit.event.inventory.InventoryType> MACHINES = java.util.EnumSet.of(
        org.bukkit.event.inventory.InventoryType.FURNACE, org.bukkit.event.inventory.InventoryType.BLAST_FURNACE,
        org.bukkit.event.inventory.InventoryType.SMOKER, org.bukkit.event.inventory.InventoryType.BREWING,
        org.bukkit.event.inventory.InventoryType.STONECUTTER, org.bukkit.event.inventory.InventoryType.LOOM,
        org.bukkit.event.inventory.InventoryType.CARTOGRAPHY, org.bukkit.event.inventory.InventoryType.BEACON,
        org.bukkit.event.inventory.InventoryType.MERCHANT, org.bukkit.event.inventory.InventoryType.CRAFTER);

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void machine(org.bukkit.event.inventory.InventoryClickEvent e) {
        org.bukkit.inventory.Inventory haut = e.getView().getTopInventory();
        if (!MACHINES.contains(haut.getType())) return;
        boolean dansMachine = e.getClickedInventory() == haut;
        ItemStack curseur = e.getCursor(), clique = e.getCurrentItem();
        ItemStack touche = e.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY && e.getHotbarButton() >= 0
            ? e.getWhoClicked().getInventory().getItem(e.getHotbarButton()) : null;
        boolean pose = dansMachine && (estNotre(curseur) || estNotre(touche)
            || (e.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND && estNotre(e.getWhoClicked().getInventory().getItemInOffHand())));
        boolean envoie = !dansMachine && e.isShiftClick() && estNotre(clique);
        if (pose || envoie) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void machineGlisser(org.bukkit.event.inventory.InventoryDragEvent e) {
        org.bukkit.inventory.Inventory haut = e.getView().getTopInventory();
        if (!MACHINES.contains(haut.getType()) || !estNotre(e.getOldCursor())) return;
        for (int s : e.getRawSlots()) if (s < haut.getSize()) { e.setCancelled(true); return; }
    }

    /** Entonnoirs : jamais un de nos objets vers une machine. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void entonnoir(org.bukkit.event.inventory.InventoryMoveItemEvent e) {
        if (MACHINES.contains(e.getDestination().getType()) && estNotre(e.getItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void fonte(org.bukkit.event.inventory.FurnaceSmeltEvent e) {
        if (estNotre(e.getSource())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void combustible(org.bukkit.event.inventory.FurnaceBurnEvent e) {
        if (estNotre(e.getFuel())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void fabricateur(org.bukkit.event.block.CrafterCraftEvent e) {
        if (e.getBlock().getState() instanceof org.bukkit.block.Crafter c) {
            for (ItemStack i : c.getInventory().getContents()) if (estNotre(i)) { e.setCancelled(true); return; }
        }
    }

    /** Composteur, feu de camp, présentoir : un clic droit avec un de nos objets n'y dépose rien. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void deposer(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || !estNotre(e.getItem())) return;
        org.bukkit.Material m = e.getClickedBlock().getType();
        if (m == org.bukkit.Material.COMPOSTER || m == org.bukkit.Material.CAMPFIRE || m == org.bukkit.Material.SOUL_CAMPFIRE
            || m == org.bukkit.Material.DECORATED_POT || m == org.bukkit.Material.CHISELED_BOOKSHELF || m == org.bukkit.Material.JUKEBOX) {
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        }
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

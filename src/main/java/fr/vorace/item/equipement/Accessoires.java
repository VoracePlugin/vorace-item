package fr.vorace.item.equipement;

import fr.vorace.item.Textes;
import fr.vorace.item.VoraceItem;
import fr.vorace.item.definitions.ModeleObjet;
import fr.vorace.item.exemplaire.Exemplaire;
import fr.vorace.item.interfaces.EcouteurMenus;
import fr.vorace.item.interfaces.Menu;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Les emplacements que le jeu n'a pas : le dos (cape) et les accessoires (anneaux, amulettes, fourreau, carquois…). Le
 * joueur les pose dans le menu {@code /equipement} ; ils sont gardés avec le joueur (données du joueur) et comptent dans
 * son équipement comme une pièce d'armure portée.
 *
 * <p>Cases : {@link #CASE_DOS} pour la cape, puis {@code equipement.accessoires} cases d'accessoire (2 par défaut, 4 au
 * plus). Un objet qui n'a pas le bon emplacement est refusé.
 */
public final class Accessoires implements Listener, TabExecutor {

    /** Emplacement d'une cape. */
    public static final String DOS = "dos";
    /** Emplacement d'un accessoire. */
    public static final String ACCESSOIRE = "accessoire";
    static final int CASE_DOS = 2, PREMIER_ACCESSOIRE = 4;

    private final VoraceItem plugin;
    private final NamespacedKey cle;

    public Accessoires(VoraceItem plugin) {
        this.plugin = plugin;
        this.cle = fr.vorace.item.exemplaire.Exemplaires.cle("accessoires");
    }

    /** Nombre de cases d'accessoire (config {@code equipement.accessoires}, de 1 à 4). */
    public int nombre() {
        return Math.max(1, Math.min(4, plugin.getConfig().getInt("equipement.accessoires", 2)));
    }

    /** Emplacement attendu dans une case du menu (null : case qui n'est pas un emplacement). */
    static String emplacementDeCase(int c, int accessoires) {
        if (c == CASE_DOS) return DOS;
        if (c >= PREMIER_ACCESSOIRE && c < PREMIER_ACCESSOIRE + accessoires) return ACCESSOIRE;
        return null;
    }

    // ------------------------------------------------------------------ données du joueur

    /** Les objets posés (dos d'abord, puis les accessoires) ; une case vide est null. */
    public List<ItemStack> lire(Player p) {
        List<ItemStack> l = new ArrayList<>();
        String brut = p.getPersistentDataContainer().get(cle, PersistentDataType.STRING);
        if (brut != null && !brut.isEmpty()) {
            for (String morceau : brut.split(";", -1)) {
                if (morceau.isEmpty()) { l.add(null); continue; }
                try {
                    l.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(morceau)));
                } catch (RuntimeException e) {
                    l.add(null);
                    plugin.getLogger().warning("Accessoire illisible pour " + p.getName() + " : " + e);
                }
            }
        }
        while (l.size() < 1 + nombre()) l.add(null);
        return l;
    }

    void ecrire(Player p, List<ItemStack> objets) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < objets.size(); i++) {
            if (i > 0) b.append(';');
            ItemStack o = objets.get(i);
            if (o != null && !o.getType().isAir()) b.append(Base64.getEncoder().encodeToString(o.serializeAsBytes()));
        }
        p.getPersistentDataContainer().set(cle, PersistentDataType.STRING, b.toString());
    }

    /** Ce qui compte dans l'équipement : objet de vorace-item, à la bonne place, pas cassé, utilisable. */
    List<Equipement.Porte> portes(Player p) {
        List<Equipement.Porte> l = new ArrayList<>();
        List<ItemStack> objets = lire(p);
        for (int i = 0; i < objets.size(); i++) {
            ItemStack o = objets.get(i);
            String attendu = i == 0 ? DOS : ACCESSOIRE;
            if (i > nombre()) break;
            Exemplaire ex = plugin.exemplaires().lire(o);
            if (ex == null || ex.casse()) continue;
            ModeleObjet m = plugin.catalogue().modele(ex.modele);
            if (m == null || !attendu.equals(m.type().emplacement()) || !plugin.equipement().utilisable(p, m)) continue;
            l.add(new Equipement.Porte(org.bukkit.inventory.EquipmentSlot.BODY, o, ex, m));
        }
        return l;
    }

    // ------------------------------------------------------------------ menu

    /** Le coffre du menu (une ligne). */
    public static final class Fenetre implements InventoryHolder {
        final Inventory inventaire;
        final int accessoires;

        Fenetre(int accessoires) {
            this.accessoires = accessoires;
            this.inventaire = Bukkit.createInventory(this, 9, Textes.objet(Menu.TITRE + "Équipement</gradient>"));
        }

        @Override
        public @NotNull Inventory getInventory() { return inventaire; }
    }

    public void ouvrir(Player p) {
        int n = nombre();
        Fenetre f = new Fenetre(n);
        ItemStack vitre = Menu.icone("gray_stained_glass_pane", " ");
        for (int i = 0; i < 9; i++) f.inventaire.setItem(i, vitre);
        List<ItemStack> objets = lire(p);
        f.inventaire.setItem(CASE_DOS, objets.get(0));
        for (int i = 0; i < n; i++) f.inventaire.setItem(PREMIER_ACCESSOIRE + i, objets.get(1 + i));
        f.inventaire.setItem(0, Menu.icone("leather_chestplate", Menu.TITRE + "Ton équipement</gradient>",
            "Case " + (CASE_DOS + 1) + " : ta cape.", "Cases suivantes : " + n + " accessoire" + (n > 1 ? "s" : "") + " (anneau, amulette, fourreau, carquois…).",
            Menu.DISCRET + "Ils comptent comme une pièce d'armure portée."));
        p.openInventory(f.inventaire);
    }

    private boolean accepte(int c, ItemStack o, int accessoires) {
        if (o == null || o.getType().isAir()) return true;
        String attendu = emplacementDeCase(c, accessoires);
        if (attendu == null) return false;
        Exemplaire ex = plugin.exemplaires().lire(o);
        ModeleObjet m = ex == null ? null : plugin.catalogue().modele(ex.modele);
        return m != null && attendu.equals(m.type().emplacement()) && o.getAmount() == 1;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void clic(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Fenetre f) || !(e.getWhoClicked() instanceof Player p)) return;
        int brut = e.getRawSlot();
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR || e.isShiftClick() || e.getAction() == InventoryAction.HOTBAR_SWAP) {
            e.setCancelled(true);   // simple et sûr : on pose et on reprend à la main
            return;
        }
        if (brut < 0 || brut >= 9) return;   // inventaire du joueur
        if (emplacementDeCase(brut, f.accessoires) == null) { e.setCancelled(true); return; }
        ItemStack pose = e.getCursor();
        if (pose != null && !pose.getType().isAir() && !accepte(brut, pose, f.accessoires)) {
            e.setCancelled(true);
            p.sendMessage(Textes.message("<red>" + (brut == CASE_DOS ? "Seule une cape se porte sur le dos." : "Seul un accessoire va dans cette case.")));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void glisser(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof Fenetre)) return;
        for (int brut : e.getRawSlots()) if (brut < 9) { e.setCancelled(true); return; }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void fermeture(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Fenetre f) || !(e.getPlayer() instanceof Player p)) return;
        List<ItemStack> objets = new ArrayList<>();
        ItemStack dos = e.getInventory().getItem(CASE_DOS);
        if (accepte(CASE_DOS, dos, f.accessoires)) objets.add(dos);
        else { objets.add(null); EcouteurMenus.rendre(p, dos); }
        for (int i = 0; i < f.accessoires; i++) {
            ItemStack o = e.getInventory().getItem(PREMIER_ACCESSOIRE + i);
            if (accepte(PREMIER_ACCESSOIRE + i, o, f.accessoires)) objets.add(o);
            else { objets.add(null); EcouteurMenus.rendre(p, o); }
        }
        // cases au-delà du nombre réglé (réglage baissé) : rendues au joueur
        List<ItemStack> avant = lire(p);
        for (int i = 1 + f.accessoires; i < avant.size(); i++) if (avant.get(i) != null) EcouteurMenus.rendre(p, avant.get(i));
        ecrire(p, objets);
        plugin.equipement().oublier(p);
        plugin.equipement().appliquerAttributs(p);
    }

    // ------------------------------------------------------------------ /equipement

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("En jeu seulement."); return true; }
        ouvrir(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        return List.of();
    }
}

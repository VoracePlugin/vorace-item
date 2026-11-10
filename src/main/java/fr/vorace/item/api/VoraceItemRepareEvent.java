package fr.vorace.item.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Un objet vient d'être réparé. Le plugin des métiers s'en sert pour l'apprentissage des recettes par la réparation :
 * la réparation ne compte que si l'objet avait perdu assez de durabilité en combat.
 */
public final class VoraceItemRepareEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player reparateur;
    private final ItemStack objet;
    private final String modele;
    private final double partPerdue;
    private final int usureCombat;

    public VoraceItemRepareEvent(Player reparateur, ItemStack objet, String modele, double partPerdue, int usureCombat) {
        this.reparateur = reparateur;
        this.objet = objet;
        this.modele = modele;
        this.partPerdue = partPerdue;
        this.usureCombat = usureCombat;
    }

    /** Le joueur qui a réparé (null si la réparation vient d'une commande ou d'un autre plugin). */
    public Player getReparateur() { return reparateur; }
    /** L'objet, déjà réparé. */
    public ItemStack getObjet() { return objet; }
    /** Identifiant du modèle de l'objet. */
    public String getModele() { return modele; }
    /** Part de la durabilité maximale qui manquait avant la réparation (0 à 1). */
    public double getPartPerdue() { return partPerdue; }
    /** Durabilité perdue en combat depuis la réparation précédente. */
    public int getUsureCombat() { return usureCombat; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

package fr.vorace.item.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/** Un objet vient d'être fabriqué à la forge ou lâché par un monstre. L'objet peut être remplacé. */
public final class VoraceItemCreeEvent extends Event {

    /** D'où vient l'objet. */
    public enum Origine { FORGE, BUTIN }

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player joueur;
    private final String modele;
    private final Origine origine;
    private ItemStack objet;

    public VoraceItemCreeEvent(Player joueur, String modele, ItemStack objet, Origine origine) {
        this.joueur = joueur;
        this.modele = modele;
        this.objet = objet;
        this.origine = origine;
    }

    /** Le forgeron, ou le joueur qui a tué le monstre (peut être null pour un butin). */
    public Player getJoueur() { return joueur; }
    public String getModele() { return modele; }
    public Origine getOrigine() { return origine; }
    public ItemStack getObjet() { return objet; }
    public void setObjet(ItemStack objet) { if (objet != null) this.objet = objet; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

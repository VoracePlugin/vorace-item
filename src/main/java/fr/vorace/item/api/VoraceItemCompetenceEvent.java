package fr.vorace.item.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/** Un joueur va lancer une compétence d'arme (avant que le mana et la stamina soient dépensés). Annulable. */
public final class VoraceItemCompetenceEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player joueur;
    private final String competence;
    private final ItemStack objet;
    private boolean annule;

    public VoraceItemCompetenceEvent(Player joueur, String competence, ItemStack objet) {
        this.joueur = joueur;
        this.competence = competence;
        this.objet = objet;
    }

    public Player getJoueur() { return joueur; }
    /** Identifiant de la compétence (competences.yml). */
    public String getCompetence() { return competence; }
    /** L'objet qui porte la compétence (null si elle est lancée par une commande ou un autre plugin). */
    public ItemStack getObjet() { return objet; }

    @Override public boolean isCancelled() { return annule; }
    @Override public void setCancelled(boolean c) { annule = c; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

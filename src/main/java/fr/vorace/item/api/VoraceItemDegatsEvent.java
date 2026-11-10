package fr.vorace.item.api;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Un joueur va infliger des dégâts calculés par vorace-item (coup normal, tir ou compétence d'arme). Les dégâts sont
 * modifiables (bonus et malus de classe, buffs) et le coup annulable. Les altérations d'état et les effets liés au
 * type ou à l'élément du coup se branchent ici.
 */
public final class VoraceItemDegatsEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player attaquant;
    private final LivingEntity cible;
    private final boolean magique;
    private final boolean critique;
    private final String typeAttaque;
    private final String element;
    private final String competence;
    private double degats;
    private boolean annule;

    public VoraceItemDegatsEvent(Player attaquant, LivingEntity cible, double degats, boolean magique, boolean critique,
                                 String typeAttaque, String element, String competence) {
        this.attaquant = attaquant;
        this.cible = cible;
        this.degats = degats;
        this.magique = magique;
        this.critique = critique;
        this.typeAttaque = typeAttaque;
        this.element = element;
        this.competence = competence;
    }

    public Player getAttaquant() { return attaquant; }
    public LivingEntity getCible() { return cible; }
    /** Dégâts après la formule (Puissance² ÷ (Puissance + Défense)) et le critique. */
    public double getDegats() { return degats; }
    public void setDegats(double degats) { this.degats = Math.max(0, degats); }
    public boolean isMagique() { return magique; }
    public boolean isCritique() { return critique; }
    /** Type d'attaque de l'arme (Cutting, Piercing...), ou null à mains nues. */
    public String getTypeAttaque() { return typeAttaque; }
    /** Élément de l'arme (« fire », « ice »...), ou null. */
    public String getElement() { return element; }
    /** Identifiant de la compétence d'arme qui porte le coup, ou null pour un coup normal. */
    public String getCompetence() { return competence; }

    @Override public boolean isCancelled() { return annule; }
    @Override public void setCancelled(boolean c) { annule = c; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

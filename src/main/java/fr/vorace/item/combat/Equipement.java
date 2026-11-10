package fr.vorace.item.combat;

import fr.vorace.item.VoraceItem;
import fr.vorace.item.api.ProfilJoueur;
import fr.vorace.item.definitions.ModeleObjet;
import fr.vorace.item.exemplaire.Exemplaire;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ce que porte un joueur : l'arme en main, l'objet de la seconde main et les quatre pièces d'armure. Un objet ne
 * compte que s'il est à sa place, s'il n'est pas cassé et si le joueur remplit ses conditions (niveau, classe).
 */
public final class Equipement {

    /** Un objet de vorace-item porté par le joueur. */
    public record Porte(EquipmentSlot place, ItemStack objet, Exemplaire exemplaire, ModeleObjet modele) {}

    /**
     * Total des stats de l'équipement.
     *
     * @param arme   l'objet tenu en main s'il compte (null à mains nues ou si l'objet est inutilisable)
     * @param portes tous les objets qui comptent
     */
    public record Bilan(Map<String, Double> totaux, Porte arme, List<Porte> portes) {
        public double total(String stat) {
            return totaux.getOrDefault(stat, 0.0);
        }

        /** Somme des dégâts élémentaires (« fire-damage », « ice-damage »...). */
        public double elementaire() {
            double s = 0;
            for (Map.Entry<String, Double> e : totaux.entrySet()) {
                String k = e.getKey();
                if (k.endsWith("-damage") && fr.vorace.item.definitions.Modificateurs.ELEMENTS.containsKey(k.substring(0, k.length() - 7))) s += e.getValue();
            }
            return s;
        }
    }

    private static final Bilan VIDE = new Bilan(Map.of(), null, List.of());
    private static final EquipmentSlot[] PLACES = {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD,
        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private record EnCache(int tick, Bilan bilan) {}

    private final VoraceItem plugin;
    private final Map<UUID, EnCache> cache = new HashMap<>();
    private final NamespacedKey cleAttribut = fr.vorace.item.exemplaire.Exemplaires.cle("equipement");

    public Equipement(VoraceItem plugin) {
        this.plugin = plugin;
    }

    public void oublier(Player joueur) {
        cache.remove(joueur.getUniqueId());
    }

    public void toutOublier() {
        cache.clear();
    }

    /** Bilan de l'équipement (gardé quelques tics : il est demandé à chaque coup). */
    public Bilan bilan(Player joueur) {
        int maintenant = Bukkit.getCurrentTick();
        EnCache c = cache.get(joueur.getUniqueId());
        if (c != null && maintenant - c.tick() < 5 && maintenant >= c.tick()) return c.bilan();
        Bilan b = calculer(joueur);
        cache.put(joueur.getUniqueId(), new EnCache(maintenant, b));
        return b;
    }

    private Bilan calculer(Player joueur) {
        Map<String, Double> totaux = null;
        List<Porte> portes = null;
        Porte arme = null;
        for (EquipmentSlot place : PLACES) {
            ItemStack objet = joueur.getInventory().getItem(place);
            Exemplaire ex = plugin.exemplaires().lire(objet);
            if (ex == null || ex.casse()) continue;
            ModeleObjet m = plugin.catalogue().modele(ex.modele);
            if (m == null || !place(m.type().emplacement()).equals(place) || !utilisable(joueur, m)) continue;
            if (totaux == null) { totaux = new LinkedHashMap<>(); portes = new ArrayList<>(); }
            Porte p = new Porte(place, objet, ex, m);
            portes.add(p);
            if (place == EquipmentSlot.HAND) arme = p;
            for (Map.Entry<String, Double> e : plugin.exemplaires().stats(ex).entrySet()) totaux.merge(e.getKey(), e.getValue(), Double::sum);
        }
        return totaux == null ? VIDE : new Bilan(totaux, arme, portes);
    }

    /** Vrai si le joueur remplit les conditions du modèle (niveau et classe). */
    public boolean utilisable(Player joueur, ModeleObjet m) {
        if (m.niveau() <= 0 && m.classes().isEmpty()) return true;
        ProfilJoueur profil = plugin.profil();
        if (m.niveau() > 0 && profil.niveau(joueur) < m.niveau()) return false;
        if (m.classes().isEmpty()) return true;
        for (String c : m.classes()) if (profil.aClasse(joueur, c)) return true;
        return false;
    }

    static EquipmentSlot place(String emplacement) {
        return switch (emplacement) {
            case "main" -> EquipmentSlot.HAND;
            case "seconde-main" -> EquipmentSlot.OFF_HAND;
            case "tete" -> EquipmentSlot.HEAD;
            case "torse" -> EquipmentSlot.CHEST;
            case "jambes" -> EquipmentSlot.LEGS;
            case "pieds" -> EquipmentSlot.FEET;
            default -> EquipmentSlot.BODY; // « aucun » : jamais porté par un joueur
        };
    }

    /**
     * Applique au joueur les modificateurs d'équipement qui passent par les attributs du jeu : vie maximale,
     * vitesse de déplacement, vitesse d'attaque, résistance au recul, chance.
     */
    public void appliquerAttributs(Player joueur) {
        Bilan b = bilan(joueur);
        attribut(joueur, Attribute.MAX_HEALTH, b.total("max-health"), AttributeModifier.Operation.ADD_NUMBER);
        attribut(joueur, Attribute.MOVEMENT_SPEED, b.total("movement-speed") / 100.0, AttributeModifier.Operation.ADD_SCALAR);
        attribut(joueur, Attribute.ATTACK_SPEED, b.total("attack-speed") / 100.0, AttributeModifier.Operation.ADD_SCALAR);
        attribut(joueur, Attribute.KNOCKBACK_RESISTANCE, b.total("knockback-resistance") / 100.0, AttributeModifier.Operation.ADD_NUMBER);
        attribut(joueur, Attribute.LUCK, b.total("luck"), AttributeModifier.Operation.ADD_NUMBER);
    }

    /** Retire tous les modificateurs d'attributs de vorace-item (arrêt du plugin). */
    public void retirerAttributs(Player joueur) {
        for (Attribute a : new Attribute[]{Attribute.MAX_HEALTH, Attribute.MOVEMENT_SPEED, Attribute.ATTACK_SPEED, Attribute.KNOCKBACK_RESISTANCE, Attribute.LUCK}) {
            AttributeInstance i = joueur.getAttribute(a);
            if (i != null) i.removeModifier((Key) cleAttribut);
        }
    }

    private void attribut(Player joueur, Attribute a, double valeur, AttributeModifier.Operation op) {
        AttributeInstance i = joueur.getAttribute(a);
        if (i == null) return;
        AttributeModifier actuel = i.getModifier((Key) cleAttribut);
        if (actuel != null && Math.abs(actuel.getAmount() - valeur) < 1e-9 && actuel.getOperation() == op) return;
        if (actuel == null && valeur == 0) return;
        i.removeModifier((Key) cleAttribut);
        if (valeur != 0) i.addModifier(new AttributeModifier(cleAttribut, valeur, op));
        if (a == Attribute.MAX_HEALTH && joueur.getHealth() > i.getValue()) joueur.setHealth(i.getValue());
    }
}

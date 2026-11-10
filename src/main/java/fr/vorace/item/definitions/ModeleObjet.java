package fr.vorace.item.definitions;

import java.util.List;
import java.util.Map;

/**
 * Un modèle d'objet (plugins/VoraceItem/objets/*.yml) : une arme, une armure, une gemme... Chaque modèle a un rang
 * de base et n'existe qu'à ce rang, au rang du dessous ou au rang du dessus.
 *
 * @param stats        valeurs de référence au rang Commun (bas de la fourchette Commune) des stats qui suivent le rang
 * @param modificateurs modificateurs fixes (ils ne changent ni avec le rang ni avec la qualité)
 * @param lignesBonus  modificateurs qui peuvent être tirés comme lignes bonus
 * @param durabilite   0 = l'objet ne s'use pas
 * @param gemmes       nombre d'emplacements de gemmes ; −1 = celui du rang
 * @param kit          non null pour une arme modulaire (assemblée à partir de pièces)
 */
public record ModeleObjet(String id, String fichier, String nom, TypeObjet type, Rarete rang, int etage, Apparence apparence,
                          Map<String, Double> stats, Map<String, String> scales, String nature, String typeAttaque,
                          String element, double vitesseAttaque, Map<String, Double> modificateurs, List<String> lignesBonus,
                          int durabilite, int niveau, List<String> classes, Map<String, String> competences, int gemmes,
                          Recette recette, List<String> description, Kit kit) {

    /**
     * Apparence de l'objet. Dans l'ordre : un objet de vorace-core, un modèle 3D ou une texture de
     * plugins/VoraceItem/ressources, sinon l'objet Minecraft de base tel quel.
     */
    public record Apparence(String materiau, String objetVorace, String modele, String texture, Integer cmd, Boolean brillance) {
        public boolean dansLePack() { return modele != null || texture != null; }
    }

    /** Ce que la forge demande pour fabriquer l'objet : « IRON_INGOT », « vorace:lingot_fer » ou « item:rubis_taille ». */
    public record Recette(Map<String, Integer> materiaux, int quantite) {}

    /**
     * Arme modulaire : une pièce par emplacement, chacune liée à un matériau.
     *
     * @param rangMax    rang le plus haut d'une arme assemblée (au-dessus, ce sont des modèles faits main)
     * @param formatNom  « &lt;type&gt; &lt;materiau&gt; » : nom de l'arme, d'après le matériau de sa première pièce
     * @param affichage  réglages « display » communs à toutes les pièces (JSON du modèle Minecraft)
     * @param gravures   rang → modèle de gravure posé sur l'arme (facultatif)
     */
    public record Kit(List<String> emplacements, Rarete rangMax, String formatNom, com.google.gson.JsonObject affichage,
                      Map<String, String> gravures) {}

    public boolean modulaire() { return kit != null; }
    public boolean magique() { return nature.equals("magique"); }
    public boolean empilable() { return type.empilable(); }

    /** Palier (tranche de 10 étages) du modèle. */
    public int palier() { return Raretes.palier(etage); }
}

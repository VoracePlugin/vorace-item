package fr.vorace.item.definitions;

import java.util.Map;

/**
 * Un matériau de forge (plugins/VoraceItem/materiaux/*.yml) et ses pièces d'armes modulaires.
 *
 * @param complement ce qui suit le nom de l'arme : « de fer », « d'acier », « en os de Minotaure »
 * @param etage      étage du donjon du matériau (son palier fixe la force des pièces)
 * @param poidsBonus modificateur → poids : les lignes bonus des objets de ce matériau restent tirées au hasard, mais
 *                   celles-ci sortent plus souvent (poids 1 pour les autres)
 */
public record Materiau(String id, String nom, String complement, int etage, Map<String, Piece> pieces, Map<String, Double> poidsBonus) {

    /**
     * Une pièce d'arme modulaire : une lame, une garde, une poignée, un pommeau...
     *
     * @param id          « matériau/pièce » (c'est aussi le nom de son apparence dans le pack)
     * @param kit         modèle modulaire auquel elle appartient (« epee »)
     * @param emplacement place dans le kit (« lame », « garde »...)
     * @param modele      modèle 3D dans plugins/VoraceItem/ressources/modeles (sans « .json »)
     * @param base        stats de référence apportées, avant le multiplicateur de palier du matériau
     * @param cout        ce que la forge demande pour cette pièce
     */
    public record Piece(String id, String materiau, String cle, String kit, String emplacement, String nom, String modele,
                        Map<String, Double> base, Map<String, Double> modificateurs, Map<String, Integer> cout, int durabilite) {}

    public int palier() { return Raretes.palier(etage); }
}

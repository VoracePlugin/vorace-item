package fr.vorace.item.exemplaire;

import fr.vorace.item.Textes;
import fr.vorace.item.definitions.Catalogue;
import fr.vorace.item.definitions.Materiau;
import fr.vorace.item.definitions.Materiau.Piece;
import fr.vorace.item.definitions.ModeleObjet;
import fr.vorace.item.definitions.Modificateurs;
import fr.vorace.item.definitions.Rarete;
import fr.vorace.item.definitions.TypeObjet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mise en page d'un objet : son nom (à la couleur du rang) et les lignes de sa description, au format MiniMessage.
 * Les blocs (rang, stats, modificateurs, gemmes, compétences, conditions, texte, durabilité) sont séparés par une ligne vide.
 */
public final class Description {

    private static final String GRIS = "<#a8a8a8>";
    private static final String DISCRET = "<#6f6f6f>";
    private static final String FIXE = "<#8fd18f>";
    private static final String BONUS = "<#7db8ff>";
    private static final String GEMME = "<#d39bff>";

    private Description() {}

    /** Nom affiché : le nom donné, sinon celui du modèle (« Épée de fer » pour une arme modulaire), puis « +3 ». */
    public static String nom(Catalogue c, ModeleObjet m, Exemplaire ex) {
        Rarete rang = c.raretes().parId(ex.rang);
        String couleur = rang == null ? "<white>" : rang.balise();
        String nom = ex.nom != null && !ex.nom.isBlank() ? Textes.echapper(ex.nom) : nomSimple(c, m, ex);
        return couleur + nom + (ex.amelioration > 0 ? " <white>+" + ex.amelioration : "");
    }

    /** Nom sans couleur : celui du modèle, ou « type + matériau » pour une arme modulaire. */
    public static String nomSimple(Catalogue c, ModeleObjet m, Exemplaire ex) {
        if (m.kit() == null || ex.pieces.isEmpty()) return m.nom();
        Piece premiere = c.piece(ex.pieces.values().iterator().next());
        Materiau mat = premiere == null ? null : c.materiau(premiere.materiau());
        String format = m.kit().formatNom();
        return format.replace("<type>", m.nom()).replace("<materiau>", mat == null ? "" : mat.complement())
            .replace("<piece>", premiere == null ? "" : premiere.nom()).trim();
    }

    /**
     * Lignes de la description.
     *
     * @param stats stats calculées de l'exemplaire (voir {@link Calcul#stats})
     */
    public static List<String> lignes(Catalogue c, ModeleObjet m, Exemplaire ex, Map<String, Double> stats) {
        Modificateurs mods = c.modificateurs();
        List<List<String>> blocs = new ArrayList<>();

        // rang, type, qualité
        List<String> entete = new ArrayList<>();
        Rarete rang = c.raretes().parId(ex.rang);
        if (rang != null) entete.add(rang.balise() + "◆ " + rang.feminin() + " " + DISCRET + "· " + m.type().nom());
        Qualite.Grade grade = Qualite.Grade.parId(ex.grade);
        if (grade != null) entete.add(GRIS + "Qualité : " + grade.couleur() + grade.nom());
        blocs.add(entete);

        // stats de rang, puis ce qui décrit le coup
        List<String> principal = new ArrayList<>();
        Map<String, Double> references = Calcul.references(c, m, ex);
        for (String stat : references.keySet()) {
            Double v = stats.get(stat);
            if (v != null && v != 0) principal.add(GRIS + mods.nom(stat) + " <white>" + Textes.nombre1(v) + (mods.pourcent(stat) ? " %" : ""));
        }
        if (m.type().estArme()) {
            if (m.vitesseAttaque() > 0) principal.add(GRIS + "Vitesse d'attaque <white>" + Textes.nombre(m.vitesseAttaque()));
            if (!m.scales().isEmpty()) {
                StringBuilder sb = new StringBuilder(GRIS + "Scale <white>");
                boolean premier = true;
                for (Map.Entry<String, String> e : m.scales().entrySet()) {
                    if (!premier) sb.append(DISCRET).append(" · <white>");
                    sb.append(TypeObjet.abreviation(e.getKey())).append(' ').append(e.getValue());
                    premier = false;
                }
                principal.add(sb.toString());
            }
            String type = m.typeAttaque() == null || m.typeAttaque().isBlank() ? null : m.typeAttaque();
            String element = m.element() == null ? null : Modificateurs.ELEMENTS.get(m.element());
            if (type != null || element != null) {
                principal.add(GRIS + "Type <white>" + (type == null ? "" : type) + (type != null && element != null ? DISCRET + " · <white>" : "") + (element == null ? "" : element));
            }
        }
        blocs.add(principal);

        // modificateurs fixes (modèle et pièces), lignes bonus
        List<String> modificateurs = new ArrayList<>();
        Map<String, Double> fixes = new java.util.LinkedHashMap<>(m.modificateurs());
        for (String idPiece : ex.pieces.values()) {
            Piece p = c.piece(idPiece);
            if (p != null) p.modificateurs().forEach((k, v) -> fixes.merge(k, v, Double::sum));
        }
        if (!m.type().estGemme()) {
            fixes.forEach((k, v) -> { if (!references.containsKey(k) && v != 0) modificateurs.add(FIXE + mods.afficher(k, v)); });
        }
        ex.bonus.forEach((k, v) -> modificateurs.add(BONUS + "◇ " + mods.afficher(k, v)));
        blocs.add(modificateurs);

        // gemme : ce qu'elle donne une fois sertie
        if (m.type().estGemme()) {
            List<String> effets = new ArrayList<>();
            effets.add(GRIS + "Une fois sertie :");
            fixes.forEach((k, v) -> effets.add(GEMME + "❖ " + mods.afficher(k, v)));
            blocs.add(effets);
        }

        // gemmes serties et emplacements libres
        List<String> gemmes = new ArrayList<>();
        int emplacements = Calcul.emplacementsGemmes(c, m, ex);
        for (String g : ex.gemmes) {
            ModeleObjet gemme = c.modele(g);
            if (gemme == null) { gemmes.add(GEMME + "❖ " + g); continue; }
            StringBuilder sb = new StringBuilder(GEMME + "❖ " + gemme.nom() + DISCRET + " :");
            boolean premier = true;
            for (Map.Entry<String, Double> e : gemme.modificateurs().entrySet()) {
                sb.append(premier ? " " : ", ").append(GEMME).append(mods.afficher(e.getKey(), e.getValue()));
                premier = false;
            }
            gemmes.add(sb.toString());
        }
        for (int i = ex.gemmes.size(); i < emplacements; i++) gemmes.add(DISCRET + "❖ Emplacement de gemme libre");
        blocs.add(gemmes);

        // conditions
        List<String> conditions = new ArrayList<>();
        if (m.niveau() > 0) conditions.add(GRIS + "Niveau requis : <white>" + m.niveau());
        if (!m.classes().isEmpty()) {
            List<String> noms = new ArrayList<>();
            for (String cl : m.classes()) noms.add(Textes.majuscule(cl.replace('_', ' ')));
            conditions.add(GRIS + (noms.size() > 1 ? "Classes : <white>" : "Classe : <white>") + String.join(", ", noms));
        }
        blocs.add(conditions);

        // texte du modèle
        List<String> texte = new ArrayList<>();
        for (String l : m.description()) texte.add(l.contains("<") ? l : "<#8a8a8a><italic>" + l);
        blocs.add(texte);

        // durabilité, fabricant
        List<String> pied = new ArrayList<>();
        if (ex.aDurabilite()) {
            pied.add(ex.casse() ? "<#ff5f5f>Cassé <#a8a8a8>(0 / " + ex.durabiliteMax + ") : à réparer"
                : GRIS + "Durabilité : <white>" + ex.durabilite + DISCRET + " / " + ex.durabiliteMax);
        }
        if (ex.fabricant != null && !ex.fabricant.isBlank()) pied.add(DISCRET + "Forgé par " + GRIS + Textes.echapper(ex.fabricant));
        blocs.add(pied);

        List<String> lignes = new ArrayList<>();
        for (List<String> bloc : blocs) {
            if (bloc.isEmpty()) continue;
            if (!lignes.isEmpty()) lignes.add("");
            lignes.addAll(bloc);
        }
        return lignes;
    }
}

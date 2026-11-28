package fr.vorace.item.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.vorace.item.definitions.Catalogue;
import fr.vorace.item.definitions.Materiau.Piece;
import fr.vorace.item.definitions.ModeleObjet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Écrit la partie du pack de ressources propre à vorace-item (espace de noms « voraceitem »), dans un dossier que
 * vorace-core fusionne avec les autres packs (plugins/Vorace/pack/sources/voraceitem).
 * <ul>
 *   <li>Arme modulaire : un seul objet assemble une pièce par emplacement (modèle « composite » de Minecraft 1.21.4,
 *       chaque pièce choisie par une chaîne de custom_model_data). Toutes les combinaisons existent sans être
 *       dessinées une par une.</li>
 *   <li>Objet avec son propre modèle 3D ou sa propre texture : un modèle simple.</li>
 * </ul>
 */
public final class GenerateurPack {

    public static final String NS = "voraceitem";

    private GenerateurPack() {}

    /**
     * @param ressources plugins/VoraceItem/ressources (sous-dossiers « modeles » et « textures »)
     * @return fichiers du pack : chemin (« assets/voraceitem/... ») → contenu
     */
    public static Map<String, byte[]> generer(Catalogue c, Path ressources, List<String> problemes) {
        Map<String, byte[]> sortie = new TreeMap<>();
        Path modeles = ressources.resolve("modeles");
        Path textures = ressources.resolve("textures");
        Path equipement = ressources.resolve("equipement");
        java.util.Set<String> equipementsFaits = new java.util.HashSet<>();

        for (ModeleObjet m : c.modeles()) {
            if (m.kit() != null) {
                JsonArray couches = new JsonArray();
                int index = 0;
                for (String emplacement : m.kit().emplacements()) {
                    JsonArray cas = new JsonArray();
                    for (Piece p : c.pieces(m.id(), emplacement)) {
                        if (p.modele() == null) {
                            problemes.add("Pièce " + p.id() + " : pas de « modele », elle sera invisible");
                            continue;
                        }
                        JsonObject modele = lire(modeles.resolve(p.modele() + ".json"));
                        if (modele == null) {
                            problemes.add("Pièce " + p.id() + " : modèle introuvable ou illisible (ressources/modeles/" + p.modele() + ".json)");
                            continue;
                        }
                        if (m.kit().affichage() != null && !modele.has("display")) modele.add("display", m.kit().affichage().deepCopy());
                        relierTextures(modele, textures, sortie, problemes, "Pièce " + p.id());
                        sortie.put("assets/" + NS + "/models/item/piece/" + p.id() + ".json", octets(modele));
                        cas.add(cas(p.id(), NS + ":item/piece/" + p.id()));
                        // la pièce tenue seule dans l'inventaire : le même modèle, recentré et agrandi dans sa case
                        sortie.put("assets/" + NS + "/models/item/piece_seule/" + p.id() + ".json", octets(seule(modele, NS + ":item/piece/" + p.id())));
                        JsonObject simple = new JsonObject();
                        simple.addProperty("type", "minecraft:model");
                        simple.addProperty("model", NS + ":item/piece_seule/" + p.id());
                        JsonObject def = new JsonObject();
                        def.add("model", simple);
                        sortie.put("assets/" + NS + "/items/piece/" + p.id() + ".json", octets(def));
                    }
                    couches.add(selection(index++, cas));
                }
                if (!m.kit().gravures().isEmpty()) {
                    JsonArray cas = new JsonArray();
                    for (Map.Entry<String, String> g : m.kit().gravures().entrySet()) {
                        JsonObject modele = lire(modeles.resolve(g.getValue() + ".json"));
                        if (modele == null) {
                            problemes.add(m.id() + " : gravure du rang « " + g.getKey() + " » introuvable (ressources/modeles/" + g.getValue() + ".json)");
                            continue;
                        }
                        if (m.kit().affichage() != null && !modele.has("display")) modele.add("display", m.kit().affichage().deepCopy());
                        relierTextures(modele, textures, sortie, problemes, m.id() + " (gravure)");
                        String chemin = "gravure/" + m.id() + "/" + g.getKey();
                        sortie.put("assets/" + NS + "/models/item/" + chemin + ".json", octets(modele));
                        cas.add(cas(g.getKey(), NS + ":item/" + chemin));
                    }
                    if (!cas.isEmpty()) couches.add(selection(index, cas));
                }
                JsonObject composite = new JsonObject();
                composite.addProperty("type", "minecraft:composite");
                composite.add("models", couches);
                JsonObject def = new JsonObject();
                def.add("model", drapeaux(composite));
                sortie.put("assets/" + NS + "/items/kit/" + m.id() + ".json", octets(def));
            }
            if (m.apparence().equipement() != null && equipementsFaits.add(m.apparence().equipement())) {
                ecrireEquipement(m.apparence().equipement(), equipement, sortie, problemes, m.id());
            }
            if (m.apparence().cape() != null) {
                // cape portée dans le dos du pantin de vorace-core : modèle dans le repère du haut du tronc (VoraceAPI.capePantin)
                JsonObject mc = lire(modeles.resolve(m.apparence().cape() + ".json"));
                if (mc == null) {
                    problemes.add(m.id() + " : modèle de cape introuvable ou illisible (ressources/modeles/" + m.apparence().cape() + ".json)");
                } else {
                    relierTextures(mc, textures, sortie, problemes, m.id());
                    sortie.put("assets/" + NS + "/models/item/porte/" + m.id() + ".json", octets(mc));
                    JsonObject simple = new JsonObject();
                    simple.addProperty("type", "minecraft:model");
                    simple.addProperty("model", NS + ":item/porte/" + m.id());
                    JsonObject def = new JsonObject();
                    def.add("model", simple);
                    sortie.put("assets/" + NS + "/items/porte/" + m.id() + ".json", octets(def));
                }
            }
            if (m.apparence().armure3d() != null && equipementsFaits.add("3d:" + m.apparence().armure3d())) {
                ecrireArmure3d(m.apparence().armure3d(), m.apparence().textureArmure3d(), ressources.resolve("armure3d"), sortie, problemes, m.id());
            }
            if (m.kit() != null) {
                // déjà fait plus haut
            } else if (m.apparence().objetVorace() == null && m.apparence().dansLePack()) {
                JsonObject modele;
                if (m.apparence().modele() != null) {
                    modele = lire(modeles.resolve(m.apparence().modele() + ".json"));
                    if (modele == null) {
                        problemes.add(m.id() + " : modèle introuvable ou illisible (ressources/modeles/" + m.apparence().modele() + ".json)");
                        continue;
                    }
                    relierTextures(modele, textures, sortie, problemes, m.id());
                } else {
                    String t = copierTexture(m.apparence().texture(), textures, sortie);
                    if (t == null) {
                        problemes.add(m.id() + " : texture introuvable (ressources/textures/" + m.apparence().texture() + ".png)");
                        continue;
                    }
                    modele = new JsonObject();
                    modele.addProperty("parent", m.type().estArme() ? "minecraft:item/handheld" : "minecraft:item/generated");
                    JsonObject tex = new JsonObject();
                    tex.addProperty("layer0", t);
                    modele.add("textures", tex);
                }
                // un arc dessiné avec ses trois images de tension (<texture>_tension_0, _1, _2) : il se bande en main comme celui du jeu
                boolean arc = m.apparence().modele() == null && m.apparence().texture() != null
                    && Files.isRegularFile(textures.resolve(m.apparence().texture() + "_tension_0.png"));
                if (arc) modele.addProperty("parent", "minecraft:item/bow");
                // un arc en 3D avec ses trois modèles de tension (<modele>_tension_0, _1, _2 : corde tirée, flèche encochée)
                boolean arc3d = m.apparence().modele() != null
                    && Files.isRegularFile(modeles.resolve(m.apparence().modele() + "_tension_0.json"));
                // réglages d'affichage de l'objet (grandir un arc en main...) : ils remplacent ceux du modèle
                if (m.apparence().affichage() != null) {
                    JsonObject display = modele.has("display") && modele.get("display").isJsonObject() ? modele.getAsJsonObject("display") : new JsonObject();
                    for (Map.Entry<String, JsonElement> e : m.apparence().affichage().entrySet()) display.add(e.getKey(), e.getValue().deepCopy());
                    modele.add("display", display);
                }
                sortie.put("assets/" + NS + "/models/item/objet/" + m.id() + ".json", octets(modele));
                JsonObject simple = new JsonObject();
                simple.addProperty("type", "minecraft:model");
                simple.addProperty("model", NS + ":item/objet/" + m.id());
                JsonObject repos = arc ? arcQuiSeBande(m, simple, textures, sortie, problemes)
                    : arc3d ? arc3dQuiSeBande(m, simple, modeles, textures, sortie, problemes) : simple;
                // modèle 3D avec une version « _gauche » (gantelet retourné) : elle sert dans la main gauche
                if (m.apparence().modele() != null && Files.isRegularFile(modeles.resolve(m.apparence().modele() + "_gauche.json"))) {
                    JsonObject g = lire(modeles.resolve(m.apparence().modele() + "_gauche.json"));
                    if (g == null) {
                        problemes.add(m.id() + " : modèle de la main gauche illisible (ressources/modeles/" + m.apparence().modele() + "_gauche.json)");
                    } else {
                        relierTextures(g, textures, sortie, problemes, m.id());
                        if (m.apparence().affichage() != null) {
                            JsonObject display = g.has("display") && g.get("display").isJsonObject() ? g.getAsJsonObject("display") : new JsonObject();
                            for (Map.Entry<String, JsonElement> e : m.apparence().affichage().entrySet()) display.add(e.getKey(), e.getValue().deepCopy());
                            g.add("display", display);
                        }
                        sortie.put("assets/" + NS + "/models/item/objet/" + m.id() + "_gauche.json", octets(g));
                        repos = mainGauche(repos, NS + ":item/objet/" + m.id() + "_gauche");
                    }
                }
                // arme par paire (armes de poing) : à la première personne, le bras du joueur avec (Vorace)
                if (m.type().parPaire() && m.apparence().modele() != null) repos = avecBras(repos);
                // modèle 3D avec une texture : la texture sert d'icône dans l'inventaire (casque en 3D, dessin à plat)
                if (m.apparence().modele() != null && m.apparence().texture() != null) {
                    String t = copierTexture(m.apparence().texture(), textures, sortie);
                    if (t == null) {
                        problemes.add(m.id() + " : icône introuvable (ressources/textures/" + m.apparence().texture() + ".png)");
                    } else {
                        JsonObject plat = new JsonObject();
                        plat.addProperty("parent", "minecraft:item/generated");
                        JsonObject tex = new JsonObject();
                        tex.addProperty("layer0", t);
                        plat.add("textures", tex);
                        sortie.put("assets/" + NS + "/models/item/objet/" + m.id() + "_icone.json", octets(plat));
                        repos = icone(repos, NS + ":item/objet/" + m.id() + "_icone");
                    }
                }
                JsonObject def = new JsonObject();
                def.add("model", drapeaux(repos));
                sortie.put("assets/" + NS + "/items/objet/" + m.id() + ".json", octets(def));
            }
        }
        return sortie;
    }

    /**
     * Modèle d'une pièce montrée seule (objet d'inventaire) : il hérite du modèle de la pièce et ne change que
     * l'affichage dans l'inventaire et dans un cadre, où la pièce est recentrée et agrandie pour remplir la case.
     * En main, elle garde les réglages du kit.
     */
    static JsonObject seule(JsonObject modele, String parent) {
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE}, max = {-Double.MAX_VALUE, -Double.MAX_VALUE};
        if (modele.has("elements") && modele.get("elements").isJsonArray()) {
            for (JsonElement e : modele.getAsJsonArray("elements")) {
                if (!e.isJsonObject()) continue;
                for (String cote : List.of("from", "to")) {
                    if (!e.getAsJsonObject().has(cote)) continue;
                    JsonArray p = e.getAsJsonObject().getAsJsonArray(cote);
                    for (int i = 0; i < 2 && i < p.size(); i++) {
                        min[i] = Math.min(min[i], p.get(i).getAsDouble());
                        max[i] = Math.max(max[i], p.get(i).getAsDouble());
                    }
                }
            }
        }
        JsonObject sortie = new JsonObject();
        sortie.addProperty("parent", parent);
        if (min[0] > max[0] || min[1] > max[1]) return sortie;
        double taille = Math.max(max[0] - min[0], max[1] - min[1]);
        double echelle = Math.max(0.5, Math.min(4, 13 / Math.max(0.5, taille)));
        JsonObject vue = new JsonObject();
        vue.add("rotation", triplet(0, 0, 0));
        vue.add("translation", triplet(arrondi(-((min[0] + max[0]) / 2 - 8) * echelle), arrondi(-((min[1] + max[1]) / 2 - 8) * echelle), 0));
        vue.add("scale", triplet(arrondi(echelle), arrondi(echelle), arrondi(echelle)));
        JsonObject affichage = new JsonObject();
        affichage.add("gui", vue);
        affichage.add("fixed", vue.deepCopy());
        sortie.add("display", affichage);
        return sortie;
    }

    private static double arrondi(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static JsonArray triplet(double x, double y, double z) {
        JsonArray a = new JsonArray();
        a.add(x);
        a.add(y);
        a.add(z);
        return a;
    }

    private static JsonObject cas(String quand, String modele) {
        JsonObject m = new JsonObject();
        m.addProperty("type", "minecraft:model");
        m.addProperty("model", modele);
        JsonObject c = new JsonObject();
        c.addProperty("when", quand);
        c.add("model", m);
        return c;
    }

    private static JsonObject selection(int index, JsonArray cas) {
        JsonObject vide = new JsonObject();
        vide.addProperty("type", "minecraft:empty");
        if (cas.isEmpty()) return vide;
        JsonObject s = new JsonObject();
        s.addProperty("type", "minecraft:select");
        s.addProperty("property", "minecraft:custom_model_data");
        s.addProperty("index", index);
        s.add("cases", cas);
        s.add("fallback", vide);
        return s;
    }

    /**
     * Les modèles exportés par Blockbench citent leurs textures de plusieurs façons (« lame », « item/lame »...). Si une
     * texture du même nom existe dans ressources/textures, elle est copiée dans le pack et reliée à « voraceitem:item/... ».
     * Les textures du jeu de base et celles des autres packs restent telles quelles.
     */
    static void relierTextures(JsonObject modele, Path textures, Map<String, byte[]> sortie, List<String> problemes, String qui) {
        if (!modele.has("textures") || !modele.get("textures").isJsonObject()) return;
        JsonObject t = modele.getAsJsonObject("textures");
        Map<String, JsonElement> copie = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : t.entrySet()) copie.put(e.getKey(), e.getValue());
        for (Map.Entry<String, JsonElement> e : copie.entrySet()) {
            if (!e.getValue().isJsonPrimitive()) continue;
            String valeur = e.getValue().getAsString();
            if (valeur.startsWith("#")) continue;
            String ns = null;
            String chemin = valeur;
            int deuxPoints = valeur.indexOf(':');
            if (deuxPoints >= 0) {
                ns = valeur.substring(0, deuxPoints);
                chemin = valeur.substring(deuxPoints + 1);
            }
            if (ns != null && !ns.equals("minecraft") && !ns.equals(NS)) continue;
            List<String> candidats = new ArrayList<>(List.of(chemin));
            for (String prefixe : List.of("item/", "items/", "block/", "blocks/", "textures/")) {
                if (chemin.startsWith(prefixe)) candidats.add(chemin.substring(prefixe.length()));
            }
            candidats.add(chemin.substring(chemin.lastIndexOf('/') + 1));
            String trouve = null;
            for (String cand : candidats) {
                trouve = copierTexture(cand, textures, sortie);
                if (trouve != null) break;
            }
            if (trouve != null) t.addProperty(e.getKey(), trouve);
            else if (ns == null || ns.equals(NS)) problemes.add(qui + " : texture « " + valeur + " » absente de ressources/textures (gardée telle quelle)");
        }
    }

    /**
     * Dessin d'armure porté sur le joueur (1.21.4) : assets/voraceitem/equipment/&lt;chemin&gt;.json et ses deux couches,
     * prises dans ressources/equipement/&lt;chemin&gt;/humanoid.png (casque, plastron, bottes) et humanoid_leggings.png.
     */
    static void ecrireEquipement(String chemin, Path dossier, Map<String, byte[]> sortie, List<String> problemes, String qui) {
        JsonObject couches = new JsonObject();
        for (String couche : List.of("humanoid", "humanoid_leggings")) {
            Path png = dossier.resolve(chemin).resolve(couche + ".png");
            if (!Files.isRegularFile(png)) continue;
            try {
                sortie.put("assets/" + NS + "/textures/entity/equipment/" + couche + "/" + chemin + ".png", Files.readAllBytes(png));
            } catch (IOException e) {
                problemes.add(qui + " : " + e.getMessage());
                continue;
            }
            JsonArray l = new JsonArray();
            JsonObject t = new JsonObject();
            t.addProperty("texture", NS + ":" + chemin);
            l.add(t);
            couches.add(couche, l);
        }
        if (couches.size() == 0) { problemes.add(qui + " : dessin d'armure introuvable (ressources/equipement/" + chemin + "/humanoid.png)"); return; }
        JsonObject eq = new JsonObject();
        eq.add("layers", couches);
        sortie.put("assets/" + NS + "/equipment/" + chemin + ".json", octets(eq));
    }

    /**
     * Armure en 3D pour le mod Armor Model API : assets/voraceitem/geo/&lt;chemin&gt;.geo.json et
     * assets/voraceitem/textures/armor/&lt;texture&gt;.png, pris dans ressources/armure3d/.
     */
    static void ecrireArmure3d(String chemin, String texture, Path dossier, Map<String, byte[]> sortie, List<String> problemes, String qui) {
        String tex = texture == null ? chemin : texture;
        Path geo = dossier.resolve(chemin + ".geo.json"), png = dossier.resolve(tex + ".png");
        try {
            if (Files.isRegularFile(geo)) sortie.put("assets/" + NS + "/geo/" + chemin + ".geo.json", Files.readAllBytes(geo));
            else problemes.add(qui + " : armure 3D introuvable (ressources/armure3d/" + chemin + ".geo.json)");
            if (Files.isRegularFile(png)) sortie.put("assets/" + NS + "/textures/armor/" + tex + ".png", Files.readAllBytes(png));
            else problemes.add(qui + " : texture de l'armure 3D introuvable (ressources/armure3d/" + tex + ".png)");
        } catch (IOException e) {
            problemes.add(qui + " : " + e.getMessage());
        }
    }

    /** Copie ressources/textures/&lt;chemin&gt;.png dans le pack ; renvoie son nom dans le pack, ou null si elle n'existe pas. */
    /**
     * Définition d'un arc qui se bande (comme l'arc du jeu) : au repos son dessin, en main tirée les trois images de
     * tension selon la durée du tir (0 ; 0,65 ; 0,9 seconde... en part de 20 ticks).
     */
    /**
     * Drapeaux de custom_model_data posés par vorace-core sur les copies d'objets qu'il envoie au client (l'objet du
     * serveur ne les a jamais) : drapeau 0, « caché en main » (pendant une animation du pantin, l'objet est dans les
     * mains du pantin : rien en main, l'icône reste dans l'inventaire) ; drapeau 1, « paire » (arme par paire montrée
     * dans la main gauche : visible à la troisième personne seulement, ni à la première personne ni dans la case) ; drapeau 4,
     * « caché à la troisième personne » (pantin permanent de vorace-core 1.6 : l'objet reste à la première personne et dans
     * l'inventaire, mais plus dans la main du corps invisible du joueur en F5, où c'est le pantin qui le tient). Le drapeau
     * 5, « tendu », est lu par les arcs seulement (arcQuiSeBande).
     */
    static JsonObject drapeaux(JsonObject normal) {
        JsonObject vide = new JsonObject();
        vide.addProperty("type", "minecraft:empty");
        JsonObject cache = selonContexte(normal.deepCopy(), vide.deepCopy(), "gui", "ground", "fixed", "head");
        JsonObject paire = selonContexte(normal.deepCopy(), vide.deepCopy(), "thirdperson_righthand", "thirdperson_lefthand");
        JsonObject tiers = selonContexte(normal.deepCopy(), vide.deepCopy(), "gui", "ground", "fixed", "head",
            "firstperson_righthand", "firstperson_lefthand");
        JsonObject siPaire = new JsonObject();
        siPaire.addProperty("type", "minecraft:condition");
        siPaire.addProperty("property", "minecraft:custom_model_data");
        siPaire.addProperty("index", 1);
        siPaire.add("on_true", paire);
        siPaire.add("on_false", normal);
        JsonObject c = new JsonObject();
        c.addProperty("type", "minecraft:condition");
        c.addProperty("property", "minecraft:custom_model_data");
        c.addProperty("index", 0);
        JsonObject siTiers = new JsonObject();
        siTiers.addProperty("type", "minecraft:condition");
        siTiers.addProperty("property", "minecraft:custom_model_data");
        siTiers.addProperty("index", 4);
        siTiers.add("on_true", tiers);
        siTiers.add("on_false", siPaire);
        c.add("on_true", cache);
        c.add("on_false", siTiers);
        return c;
    }

    /** Ce modèle dans ces réglages d'affichage, l'autre ailleurs. */
    private static JsonObject selonContexte(JsonObject dedans, JsonObject ailleurs, String... contextes) {
        JsonArray quand = new JsonArray();
        for (String x : contextes) quand.add(x);
        JsonObject cas = new JsonObject();
        cas.add("when", quand);
        cas.add("model", dedans);
        JsonArray cases = new JsonArray();
        cases.add(cas);
        JsonObject s = new JsonObject();
        s.addProperty("type", "minecraft:select");
        s.addProperty("property", "minecraft:display_context");
        s.add("cases", cases);
        s.add("fallback", ailleurs);
        return s;
    }

    /** Modèle du bras droit tenu, fourni par Vorace (1.4.5) ; ses carrés : 224 (bras large), 192 (bras fin). */
    static final String BRAS_TENU = "vorace:pantin_bras/tenu_droit";
    static final int PIXELS_BRAS = 224, PIXELS_BRAS_FIN = 192;

    /**
     * Le jeu ne dessine pas le bras à la première personne quand un objet est en main : pour une arme de poing, on
     * dessine avec elle le modèle du bras de Vorace, teinté par les couleurs de custom_model_data que Vorace met sur la
     * copie envoyée au joueur (son skin ; drapeau 2 : bras fin). Sans couleurs, teinte de peau par défaut.
     */
    static JsonObject avecBras(JsonObject arme) {
        JsonObject bras = new JsonObject();
        bras.addProperty("type", "minecraft:condition");
        bras.addProperty("property", "minecraft:custom_model_data");
        bras.addProperty("index", 2);
        bras.add("on_true", brasTeinte(BRAS_TENU + "_fin", PIXELS_BRAS_FIN));
        bras.add("on_false", brasTeinte(BRAS_TENU, PIXELS_BRAS));
        JsonArray couches = new JsonArray();
        couches.add(arme.deepCopy());
        couches.add(bras);
        JsonObject composite = new JsonObject();
        composite.addProperty("type", "minecraft:composite");
        composite.add("models", couches);
        return selonContexte(composite, arme, "firstperson_righthand");
    }

    private static JsonObject brasTeinte(String modele, int pixels) {
        JsonObject b = new JsonObject();
        b.addProperty("type", "minecraft:model");
        b.addProperty("model", modele);
        JsonArray teintes = new JsonArray();
        for (int i = 0; i < pixels; i++) {
            JsonObject t = new JsonObject();
            t.addProperty("type", "minecraft:custom_model_data");
            t.addProperty("index", i);
            t.addProperty("default", 0xC89678);
            teintes.add(t);
        }
        b.add("tints", teintes);
        return b;
    }

    /** Dans la main gauche (première et troisième personne, pantin de Vorace), un autre modèle. */
    static JsonObject mainGauche(JsonObject ailleurs, String modeleGauche) {
        JsonObject g = new JsonObject();
        g.addProperty("type", "minecraft:model");
        g.addProperty("model", modeleGauche);
        JsonArray quand = new JsonArray();
        quand.add("thirdperson_lefthand");
        quand.add("firstperson_lefthand");
        JsonObject cas = new JsonObject();
        cas.add("when", quand);
        cas.add("model", g);
        JsonArray cases = new JsonArray();
        cases.add(cas);
        JsonObject s = new JsonObject();
        s.addProperty("type", "minecraft:select");
        s.addProperty("property", "minecraft:display_context");
        s.add("cases", cases);
        s.add("fallback", ailleurs);
        return s;
    }

    /** Dans l'inventaire (« gui »), le modèle à plat de l'icône ; partout ailleurs, le modèle de l'objet. */
    static JsonObject icone(JsonObject ailleurs, String modeleIcone) {
        JsonObject plat = new JsonObject();
        plat.addProperty("type", "minecraft:model");
        plat.addProperty("model", modeleIcone);
        JsonObject cas = new JsonObject();
        cas.addProperty("when", "gui");
        cas.add("model", plat);
        JsonArray cases = new JsonArray();
        cases.add(cas);
        JsonObject s = new JsonObject();
        s.addProperty("type", "minecraft:select");
        s.addProperty("property", "minecraft:display_context");
        s.add("cases", cases);
        s.add("fallback", ailleurs);
        return s;
    }

    private static JsonObject arcQuiSeBande(ModeleObjet m, JsonObject repos, Path textures, Map<String, byte[]> sortie, List<String> problemes) {
        JsonObject[] tension = new JsonObject[3];
        for (int i = 0; i < 3; i++) {
            String t = copierTexture(m.apparence().texture() + "_tension_" + i, textures, sortie);
            if (t == null) {
                problemes.add(m.id() + " : image de tension introuvable (ressources/textures/" + m.apparence().texture() + "_tension_" + i + ".png)");
                return repos;
            }
            // hérite du modèle de l'arc au repos : mêmes réglages d'affichage, seul le dessin change
            JsonObject mod = new JsonObject();
            mod.addProperty("parent", NS + ":item/objet/" + m.id());
            JsonObject tex = new JsonObject();
            tex.addProperty("layer0", t);
            mod.add("textures", tex);
            String chemin = "objet/" + m.id() + "_tension_" + i;
            sortie.put("assets/" + NS + "/models/item/" + chemin + ".json", octets(mod));
            tension[i] = new JsonObject();
            tension[i].addProperty("type", "minecraft:model");
            tension[i].addProperty("model", NS + ":item/" + chemin);
        }
        return tension(tension, repos);
    }

    /**
     * Définition d'un arc en 3D qui se bande : ses trois modèles de tension (ressources/modeles/&lt;modele&gt;_tension_0,
     * _1, _2), avec les mêmes réglages d'affichage que l'arc au repos.
     */
    private static JsonObject arc3dQuiSeBande(ModeleObjet m, JsonObject repos, Path modeles, Path textures, Map<String, byte[]> sortie, List<String> problemes) {
        JsonObject[] tension = new JsonObject[3];
        for (int i = 0; i < 3; i++) {
            JsonObject mod = lire(modeles.resolve(m.apparence().modele() + "_tension_" + i + ".json"));
            if (mod == null) {
                problemes.add(m.id() + " : modèle de tension introuvable ou illisible (ressources/modeles/" + m.apparence().modele() + "_tension_" + i + ".json)");
                return repos;
            }
            relierTextures(mod, textures, sortie, problemes, m.id());
            if (m.apparence().affichage() != null) {
                JsonObject display = mod.has("display") && mod.get("display").isJsonObject() ? mod.getAsJsonObject("display") : new JsonObject();
                for (Map.Entry<String, JsonElement> e : m.apparence().affichage().entrySet()) display.add(e.getKey(), e.getValue().deepCopy());
                mod.add("display", display);
            }
            String chemin = "objet/" + m.id() + "_tension_" + i;
            sortie.put("assets/" + NS + "/models/item/" + chemin + ".json", octets(mod));
            tension[i] = new JsonObject();
            tension[i].addProperty("type", "minecraft:model");
            tension[i].addProperty("model", NS + ":item/" + chemin);
        }
        return tension(tension, repos);
    }

    /** Tir d'un arc : au repos « repos », en main tirée les trois tensions selon la durée du tir, tendu sur le pantin. */
    private static JsonObject tension(JsonObject[] tension, JsonObject repos) {
        JsonArray paliers = new JsonArray();
        double[] seuils = {0.65, 0.9};
        for (int i = 0; i < 2; i++) {
            JsonObject e = new JsonObject();
            e.addProperty("threshold", seuils[i]);
            e.add("model", tension[i + 1]);
            paliers.add(e);
        }
        JsonObject tir = new JsonObject();
        tir.addProperty("type", "minecraft:range_dispatch");
        tir.addProperty("property", "minecraft:use_duration");
        tir.addProperty("scale", 0.05);
        tir.add("entries", paliers);
        tir.add("fallback", tension[0]);
        JsonObject c = new JsonObject();
        c.addProperty("type", "minecraft:condition");
        c.addProperty("property", "minecraft:using_item");
        c.add("on_true", tir);
        c.add("on_false", repos);
        // drapeau 5 de custom_model_data, « tendu » : posé par vorace-core sur l'arc que tient le pantin d'un joueur qui
        // vise. Un affichage d'objet n'a pas de porteur (« using_item » y est toujours faux) : sans lui, l'arc du pantin
        // resterait au repos pendant le tir.
        JsonObject tendu = new JsonObject();
        tendu.addProperty("type", "minecraft:condition");
        tendu.addProperty("property", "minecraft:custom_model_data");
        tendu.addProperty("index", TENDU);
        tendu.add("on_true", tension[2].deepCopy());
        tendu.add("on_false", c);
        return tendu;
    }

    /** Drapeau de custom_model_data « arc tendu » (voir arcQuiSeBande). */
    static final int TENDU = 5;

    static String copierTexture(String chemin, Path textures, Map<String, byte[]> sortie) {
        if (chemin == null) return null;
        Path png = textures.resolve(chemin + ".png");
        if (!Files.isRegularFile(png)) return null;
        try {
            sortie.put("assets/" + NS + "/textures/item/" + chemin + ".png", Files.readAllBytes(png));
            Path anim = textures.resolve(chemin + ".png.mcmeta");
            if (Files.isRegularFile(anim)) sortie.put("assets/" + NS + "/textures/item/" + chemin + ".png.mcmeta", Files.readAllBytes(anim));
            return NS + ":item/" + chemin;
        } catch (IOException e) {
            return null;
        }
    }

    private static JsonObject lire(Path f) {
        try {
            if (!Files.isRegularFile(f)) return null;
            JsonElement e = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static byte[] octets(JsonObject o) {
        return o.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Écrit les fichiers dans le dossier (ceux qui n'en font plus partie sont retirés).
     *
     * @return vrai si quelque chose a changé sur le disque
     */
    public static boolean ecrire(Path dossier, Map<String, byte[]> fichiers) throws IOException {
        boolean change = false;
        Files.createDirectories(dossier);
        List<Path> presents;
        try (Stream<Path> s = Files.walk(dossier)) {
            presents = s.filter(Files::isRegularFile).toList();
        }
        for (Path p : presents) {
            String rel = dossier.relativize(p).toString().replace('\\', '/');
            if (!fichiers.containsKey(rel)) {
                Files.delete(p);
                change = true;
            }
        }
        for (Map.Entry<String, byte[]> e : fichiers.entrySet()) {
            Path cible = dossier.resolve(e.getKey());
            if (Files.isRegularFile(cible) && Arrays.equals(Files.readAllBytes(cible), e.getValue())) continue;
            Files.createDirectories(cible.getParent());
            Files.write(cible, e.getValue());
            change = true;
        }
        return change;
    }
}

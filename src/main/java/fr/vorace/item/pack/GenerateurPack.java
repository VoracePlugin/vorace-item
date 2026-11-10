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
                def.add("model", composite);
                sortie.put("assets/" + NS + "/items/kit/" + m.id() + ".json", octets(def));
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
                sortie.put("assets/" + NS + "/models/item/objet/" + m.id() + ".json", octets(modele));
                JsonObject simple = new JsonObject();
                simple.addProperty("type", "minecraft:model");
                simple.addProperty("model", NS + ":item/objet/" + m.id());
                JsonObject def = new JsonObject();
                def.add("model", simple);
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

    /** Copie ressources/textures/&lt;chemin&gt;.png dans le pack ; renvoie son nom dans le pack, ou null si elle n'existe pas. */
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

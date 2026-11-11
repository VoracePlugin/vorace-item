package fr.vorace.item.definitions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import fr.vorace.item.Textes;
import fr.vorace.item.definitions.Materiau.Piece;
import fr.vorace.item.definitions.ModeleObjet.Apparence;
import fr.vorace.item.definitions.ModeleObjet.Kit;
import fr.vorace.item.definitions.ModeleObjet.Recette;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Tout ce que décrivent les fichiers de plugins/VoraceItem : rangs, modificateurs, types, matériaux et pièces,
 * modèles d'objets. Un catalogue ne change plus une fois chargé : « /vitem recharger » en construit un nouveau.
 */
public final class Catalogue {

    public static final Pattern ID_VALIDE = Pattern.compile("[a-z0-9_\\-/]+");

    private final Raretes raretes;
    private final Modificateurs modificateurs;
    private final Map<String, TypeObjet> types = new LinkedHashMap<>();
    private final Map<String, Materiau> materiaux = new LinkedHashMap<>();
    /** Objets simples (armures...) : leur matériau, qui oriente le tirage des lignes bonus. */
    private final Map<String, String> materiauxDesModeles = new LinkedHashMap<>();
    private final Map<String, Piece> pieces = new LinkedHashMap<>();
    private final Map<String, ModeleObjet> modeles = new LinkedHashMap<>();

    private Catalogue(Raretes raretes, Modificateurs modificateurs) {
        this.raretes = raretes;
        this.modificateurs = modificateurs;
    }

    public Raretes raretes() { return raretes; }
    public Modificateurs modificateurs() { return modificateurs; }
    public Collection<TypeObjet> types() { return types.values(); }
    public TypeObjet type(String id) { return types.get(Textes.cle(id)); }
    public Collection<Materiau> materiaux() { return materiaux.values(); }
    public Materiau materiau(String id) { return id == null ? null : materiaux.get(Textes.cle(id)); }

    /**
     * Poids des lignes bonus d'un exemplaire : ceux du matériau de sa première pièce (arme modulaire) ou de son champ
     * « materiau » (objet simple). Vide si rien n'est déclaré : tous les modificateurs ont alors la même chance.
     */
    public Map<String, Double> poidsBonus(ModeleObjet m, fr.vorace.item.exemplaire.Exemplaire ex) {
        Materiau mat = null;
        if (m.kit() != null && ex != null && !m.kit().emplacements().isEmpty()) {
            Piece p = piece(ex.pieces.get(m.kit().emplacements().get(0)));
            if (p != null) mat = materiau(p.materiau());
        } else {
            mat = materiau(materiauxDesModeles.get(m.id()));
        }
        return mat == null ? Map.of() : mat.poidsBonus();
    }
    public Collection<ModeleObjet> modeles() { return modeles.values(); }
    public ModeleObjet modele(String id) { return id == null ? null : modeles.get(id.toLowerCase(Locale.ROOT)); }
    public Collection<Piece> pieces() { return pieces.values(); }
    public Piece piece(String id) { return id == null ? null : pieces.get(id.toLowerCase(Locale.ROOT)); }

    /** Pièces d'un kit pour un emplacement (« epee », « lame »), dans l'ordre des fichiers. */
    public List<Piece> pieces(String kit, String emplacement) {
        List<Piece> l = new ArrayList<>();
        for (Piece p : pieces.values()) if (p.kit().equals(kit) && p.emplacement().equals(emplacement)) l.add(p);
        return l;
    }

    // ------------------------------------------------------------------ chargement

    /** Charge le dossier de vorace-item. Les erreurs (non bloquantes) vont dans « problemes ». */
    public static Catalogue charger(Path dossier, List<String> problemes) {
        Raretes raretes = Raretes.lire(yaml(dossier.resolve("raretes.yml")), problemes);
        Modificateurs mods = Modificateurs.lire(yaml(dossier.resolve("modificateurs.yml")), problemes);
        Catalogue c = new Catalogue(raretes, mods);

        YamlConfiguration types = yaml(dossier.resolve("types.yml"));
        for (String id : types.getKeys(false)) {
            if (types.isConfigurationSection(id)) c.types.put(Textes.cle(id), TypeObjet.lire(Textes.cle(id), types.getConfigurationSection(id)));
        }
        if (!c.types.containsKey("materiau")) {
            c.types.put("materiau", new TypeObjet("materiau", "Matériau", "materiau", "aucun", "paper", "physique", "", Map.of(), 0, List.of(), true));
        }

        for (Path f : fichiers(dossier.resolve("materiaux"))) {
            YamlConfiguration y = yaml(f);
            String nom = dossier.relativize(f).toString().replace('\\', '/');
            for (String id : y.getKeys(false)) {
                if (!y.isConfigurationSection(id)) continue;
                String propre = id.toLowerCase(Locale.ROOT);
                if (!ID_VALIDE.matcher(propre).matches() || propre.contains("/")) {
                    problemes.add(nom + " : identifiant de matériau invalide « " + id + " » (lettres minuscules, chiffres, _ -)");
                    continue;
                }
                Materiau m = c.lireMateriau(propre, y.getConfigurationSection(id), problemes);
                c.materiaux.put(propre, m);
                c.pieces.putAll(prefixer(m));
            }
        }

        for (Path f : fichiers(dossier.resolve("objets"))) {
            YamlConfiguration y = yaml(f);
            String nom = dossier.relativize(f).toString().replace('\\', '/');
            for (String id : y.getKeys(false)) {
                if (!y.isConfigurationSection(id)) continue;
                String propre = id.toLowerCase(Locale.ROOT);
                if (!ID_VALIDE.matcher(propre).matches()) {
                    problemes.add(nom + " : identifiant invalide « " + id + " » (lettres minuscules, chiffres, _ - /)");
                    continue;
                }
                if (c.modeles.containsKey(propre)) problemes.add(nom + " : « " + propre + " » existe déjà dans " + c.modeles.get(propre).fichier() + ", remplacé");
                c.modeles.put(propre, c.lireModele(propre, nom, y.getConfigurationSection(id), problemes));
            }
        }
        c.verifier(problemes);
        return c;
    }

    private static Map<String, Piece> prefixer(Materiau m) {
        Map<String, Piece> p = new LinkedHashMap<>();
        for (Piece x : m.pieces().values()) p.put(x.id(), x);
        return p;
    }

    private void verifier(List<String> problemes) {
        for (ModeleObjet m : modeles.values()) {
            if (m.kit() != null) {
                for (String e : m.kit().emplacements()) {
                    if (pieces(m.id(), e).isEmpty()) problemes.add(m.id() + " : aucune pièce pour l'emplacement « " + e + " » (voir materiaux/*.yml)");
                }
            }
        }
        for (Piece p : pieces.values()) {
            ModeleObjet kit = modeles.get(p.kit());
            if (kit == null || kit.kit() == null) problemes.add("Pièce " + p.id() + " : le modèle modulaire « " + p.kit() + " » n'existe pas");
            else if (!kit.kit().emplacements().contains(p.emplacement())) {
                problemes.add("Pièce " + p.id() + " : « " + p.emplacement() + " » n'est pas un emplacement de « " + p.kit() + " » " + kit.kit().emplacements());
            }
        }
    }

    private Materiau lireMateriau(String id, ConfigurationSection s, List<String> problemes) {
        String nom = s.getString("nom", Textes.majuscule(id));
        Map<String, Piece> pieces = new LinkedHashMap<>();
        ConfigurationSection sp = s.getConfigurationSection("pieces");
        if (sp != null) {
            for (String cle : sp.getKeys(false)) {
                ConfigurationSection p = sp.getConfigurationSection(cle);
                if (p == null) continue;
                String propre = cle.toLowerCase(Locale.ROOT);
                if (!ID_VALIDE.matcher(propre).matches() || propre.contains("/")) {
                    problemes.add("Matériau " + id + " : identifiant de pièce invalide « " + cle + " »");
                    continue;
                }
                String kit = p.getString("kit", "").toLowerCase(Locale.ROOT);
                String emplacement = Textes.cle(p.getString("emplacement", ""));
                if (kit.isEmpty() || emplacement.isEmpty()) {
                    problemes.add("Pièce " + id + "/" + propre + " : « kit » et « emplacement » sont obligatoires");
                    continue;
                }
                pieces.put(propre, new Piece(id + "/" + propre, id, propre, kit, emplacement, p.getString("nom", Textes.majuscule(propre.replace('_', ' '))),
                    chemin(p.getString("modele")), nombres(p.getConfigurationSection("base")), nombres(p.getConfigurationSection("modificateurs")),
                    quantites(p.getConfigurationSection("cout")), Math.max(0, p.getInt("durabilite", 0))));
            }
        }
        Map<String, Double> poids = new LinkedHashMap<>();
        nombres(s.getConfigurationSection("lignes-bonus")).forEach((k, v) -> poids.put(modificateurs.id(k), Math.max(0, v)));
        return new Materiau(id, nom, s.getString("complement", "en " + nom.toLowerCase(Locale.ROOT)), Math.max(1, s.getInt("etage", 1)), pieces, poids);
    }

    private ModeleObjet lireModele(String id, String fichier, ConfigurationSection s, List<String> problemes) {
        TypeObjet type = type(s.getString("type", "materiau"));
        if (type == null) {
            problemes.add(id + " : type inconnu « " + s.getString("type") + " » (voir types.yml), « materiau » utilisé");
            type = types.get("materiau");
        }
        Rarete rang = raretes.parId(s.getString("rang", raretes.premier().id()));
        if (rang == null) {
            problemes.add(id + " : rang inconnu « " + s.getString("rang") + " », " + raretes.premier().nom() + " utilisé");
            rang = raretes.premier();
        }
        // les stats du fichier sont celles du rang de base du modèle (bas de sa fourchette) : ramenées au rang Commun
        Map<String, Double> stats = new LinkedHashMap<>();
        double versCommun = raretes.premier().multiplicateur() / rang.multiplicateur();
        for (Map.Entry<String, Double> e : nombres(s.getConfigurationSection("stats")).entrySet()) stats.put(e.getKey(), e.getValue() * versCommun);

        ConfigurationSection a = s.getConfigurationSection("apparence");
        Apparence apparence = new Apparence(
            (a == null ? type.materiau() : a.getString("materiau", type.materiau())).toLowerCase(Locale.ROOT).replace("minecraft:", ""),
            a == null || a.getString("objet-vorace") == null ? null : a.getString("objet-vorace").toLowerCase(Locale.ROOT),
            a == null ? null : chemin(a.getString("modele")), a == null ? null : chemin(a.getString("texture")),
            a != null && a.contains("custom-model-data") ? a.getInt("custom-model-data") : null,
            a != null && a.contains("brillance") ? a.getBoolean("brillance") : null,
            a == null ? null : chemin(a.getString("equipement")),
            a == null ? null : chemin(a.getString("armure-3d")),
            a == null ? null : chemin(a.getString("texture-armure-3d", a.getString("armure-3d"))));
        if (s.getString("materiau") != null) materiauxDesModeles.put(id, s.getString("materiau").toLowerCase(Locale.ROOT));

        ConfigurationSection cond = s.getConfigurationSection("conditions");
        List<String> classes = new ArrayList<>();
        if (cond != null) for (String c : cond.getStringList("classes")) classes.add(Textes.cle(c));

        if (s.contains("competences")) problemes.add(id + " : « competences » ignoré (les compétences ne sont plus gérées par vorace-item)");

        Recette recette = null;
        ConfigurationSection sr = s.getConfigurationSection("recette");
        if (sr != null) {
            ConfigurationSection mat = sr.isConfigurationSection("materiaux") ? sr.getConfigurationSection("materiaux") : sr;
            Map<String, Integer> q = quantites(mat);
            q.remove("quantite");
            if (q.isEmpty()) problemes.add(id + " : recette sans matériaux, ignorée");
            else recette = new Recette(q, Math.max(1, sr.getInt("quantite", 1)));
        }

        Kit kit = null;
        ConfigurationSection sk = s.getConfigurationSection("modulaire");
        if (sk != null) {
            List<String> emplacements = new ArrayList<>();
            for (String e : sk.getStringList("emplacements")) emplacements.add(Textes.cle(e));
            if (emplacements.isEmpty()) emplacements = List.of("lame", "garde", "poignee", "pommeau");
            Rarete max = raretes.parId(sk.getString("rang-max", "epique"));
            Map<String, String> gravures = new LinkedHashMap<>();
            ConfigurationSection sg = sk.getConfigurationSection("gravures");
            if (sg != null) for (String k : sg.getKeys(false)) {
                Rarete r = raretes.parId(k);
                if (r != null && sg.getString(k) != null) gravures.put(r.id(), chemin(sg.getString(k)));
            }
            ConfigurationSection aff = sk.getConfigurationSection("affichage");
            kit = new Kit(List.copyOf(emplacements), max == null ? raretes.dernier() : max, sk.getString("nom", "<type> <materiau>"),
                aff == null ? null : json(aff), gravures);
        }

        String element = Modificateurs.element(s.getString("element"));
        if (s.getString("element") != null && element == null) problemes.add(id + " : élément inconnu « " + s.getString("element") + " » " + Modificateurs.ELEMENTS.keySet());
        List<String> lignes = new ArrayList<>();
        for (String l : s.contains("lignes-bonus") ? s.getStringList("lignes-bonus") : type.lignesBonus()) lignes.add(modificateurs.id(l));

        return new ModeleObjet(id, fichier, s.getString("nom", Textes.majuscule(id.replace('_', ' '))), type, rang,
            Math.max(1, s.getInt("etage", 1)), apparence, stats,
            s.isConfigurationSection("scales") ? TypeObjet.scales(s.getConfigurationSection("scales")) : type.scales(),
            Textes.cle(s.getString("nature", type.nature())).startsWith("mag") ? "magique" : "physique",
            s.getString("type-attaque", type.typeAttaque()), element, s.getDouble("vitesse-attaque", type.vitesseAttaque()),
            nombres(s.getConfigurationSection("modificateurs")), lignes, Math.max(0, s.getInt("durabilite", 0)),
            cond == null ? 0 : Math.max(0, cond.getInt("niveau", 0)), classes, s.getInt("gemmes", -1),
            recette, s.getStringList("description"), kit);
    }

    // ------------------------------------------------------------------ outils de lecture

    private Map<String, Double> nombres(ConfigurationSection s) {
        Map<String, Double> m = new LinkedHashMap<>();
        if (s != null) for (String k : s.getKeys(false)) if (!s.isConfigurationSection(k)) m.put(modificateurs.id(k), s.getDouble(k, 0));
        return m;
    }

    /** « IRON_INGOT: 2 », « "vorace:lingot_fer": 3 » → ingrédient en minuscules et quantité. */
    public static Map<String, Integer> quantites(ConfigurationSection s) {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (s != null) for (String k : s.getKeys(false)) {
            if (!s.isConfigurationSection(k) && s.getInt(k, 0) > 0) m.put(k.toLowerCase(Locale.ROOT).replace("minecraft:", ""), s.getInt(k));
        }
        return m;
    }

    private static String chemin(String c) {
        if (c == null || c.isBlank()) return null;
        String p = c.trim().replace('\\', '/');
        if (p.endsWith(".json")) p = p.substring(0, p.length() - 5);
        if (p.endsWith(".png")) p = p.substring(0, p.length() - 4);
        while (p.startsWith("/")) p = p.substring(1);
        return p.toLowerCase(Locale.ROOT);
    }

    /** Section YAML → objet JSON (réglages « display » des modèles). */
    public static JsonObject json(ConfigurationSection s) {
        JsonObject o = new JsonObject();
        for (String k : s.getKeys(false)) {
            if (s.isConfigurationSection(k)) o.add(k, json(s.getConfigurationSection(k)));
            else o.add(k, json(s.get(k)));
        }
        return o;
    }

    private static JsonElement json(Object v) {
        if (v instanceof List<?> l) {
            JsonArray a = new JsonArray();
            for (Object x : l) a.add(json(x));
            return a;
        }
        if (v instanceof Number n) return new JsonPrimitive(n);
        if (v instanceof Boolean b) return new JsonPrimitive(b);
        return new JsonPrimitive(String.valueOf(v));
    }

    private static YamlConfiguration yaml(Path f) {
        return Files.isRegularFile(f) ? YamlConfiguration.loadConfiguration(f.toFile()) : new YamlConfiguration();
    }

    private static List<Path> fichiers(Path dossier) {
        if (!Files.isDirectory(dossier)) return List.of();
        try (Stream<Path> f = Files.walk(dossier)) {
            return f.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".yml")).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}

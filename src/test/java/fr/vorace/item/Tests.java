package fr.vorace.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.vorace.item.combat.Formule;
import fr.vorace.item.definitions.Catalogue;
import fr.vorace.item.definitions.ModeleObjet;
import fr.vorace.item.definitions.Modificateurs;
import fr.vorace.item.definitions.Rarete;
import fr.vorace.item.definitions.Raretes;
import fr.vorace.item.definitions.TypeObjet;
import fr.vorace.item.exemplaire.Calcul;
import fr.vorace.item.exemplaire.Description;
import fr.vorace.item.exemplaire.Exemplaire;
import fr.vorace.item.exemplaire.Qualite;
import fr.vorace.item.forge.Ingredients;
import fr.vorace.item.pack.GenerateurPack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Tests sans serveur (tâche Gradle « tests ») : rangs et fourchettes, qualité, formule de dégâts, lecture des fichiers
 * d'exemple, calcul des stats d'une arme modulaire, mise en page, partie du pack de ressources.
 */
public final class Tests {

    private static int reussis, echecs;

    public static void main(String[] args) throws Exception {
        Path dossier = installerExemples();
        try {
            raretes();
            qualite();
            formule();
            List<String> problemes = new ArrayList<>();
            Catalogue c = Catalogue.charger(dossier, problemes);
            catalogue(c, problemes);
            calcul(c);
            description(c);
            pack(c, dossier);
            ingredients();
        } finally {
            supprimer(dossier);
        }
        System.out.println(reussis + " vérification(s) réussie(s), " + echecs + " échec(s)");
        System.exit(echecs == 0 ? 0 : 1);
    }

    private static void verifier(boolean ok, String quoi) {
        if (ok) reussis++;
        else {
            echecs++;
            System.out.println("ECHEC : " + quoi);
        }
    }

    private static boolean proche(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }

    // ------------------------------------------------------------------ rangs et fourchettes

    private static void raretes() {
        Raretes r = Raretes.babel();
        verifier(r.tous().size() == 7, "7 rangs");
        verifier(r.parId("Peu commune") == r.parId("peu_commun") && r.parId("épique").id().equals("epique") && r.parId("Divine").id().equals("divin"),
            "un rang se retrouve par son nom, au masculin, au féminin, avec ou sans accent");
        verifier(r.parId("inconnu") == null, "rang inconnu");
        // fourchettes contiguës : le haut d'un rang est le bas du suivant
        double[] precedente = r.fourchette(40, null, true);
        verifier(proche(precedente[0], 34) && proche(precedente[1], 40), "fourchette rebut de 34 à 40 pour une référence de 40");
        for (Rarete rang : r.tous()) {
            double[] f = r.fourchette(40, rang, false);
            verifier(proche(f[0], precedente[1]), "la fourchette " + rang.nom() + " commence où finit la précédente");
            verifier(f[1] > f[0], "la fourchette " + rang.nom() + " n'est pas vide");
            precedente = f;
        }
        verifier(proche(precedente[1], 84), "le sommet du dernier rang vaut 2,1 fois la référence");
        verifier(proche(r.valeur(40, r.parId("rare"), false, 0.5), 51), "milieu de la fourchette Rare : 51");
        verifier(Raretes.palier(1) == 1 && Raretes.palier(10) == 1 && Raretes.palier(11) == 2 && Raretes.palier(100) == 10, "paliers de 10 étages");
        verifier(proche(r.multiplicateurPalier(3), 1.12 * 1.12), "multiplicateur du palier 3");
    }

    // ------------------------------------------------------------------ qualité

    private static void qualite() {
        Raretes r = Raretes.babel();
        Qualite q = Qualite.babel(r);
        verifier(q.grade(29.9) == Qualite.Grade.BRUTE && q.grade(30) == Qualite.Grade.CORRECTE && q.grade(55) == Qualite.Grade.FINE
            && q.grade(70) == Qualite.Grade.EXCELLENTE && q.grade(85) == Qualite.Grade.CHEF_D_OEUVRE, "seuils des grades");
        Rarete commun = r.parId("commun"), rare = r.parId("rare"), epique = r.parId("epique"), legendaire = r.parId("legendaire"), divin = r.parId("divin");

        // sans aléa (bruit nul), pour vérifier les places exactes
        Qualite exacte = new Qualite(r, new double[]{30, 55, 70, 85}, 0, legendaire, new double[]{45, 50, 5});
        Random h = new Random(7);
        Qualite.Resultat brute = exacte.calculer(15, rare, 0, 0, h);
        verifier(brute.rang().id().equals("peu_commun") && !brute.rebut() && proche(brute.position(), 0.5), "Brute de score 15 : rang du dessous, milieu de sa fourchette");
        Qualite.Resultat rebut = exacte.calculer(29, commun, 0, 0, h);
        verifier(rebut.rebut() && rebut.rang() == commun && proche(rebut.position(), 29 / 30.0), "Brute de matériau Commun : fourchette rebut");
        Qualite.Resultat correcte = exacte.calculer(30, rare, 0, 0, h);
        verifier(correcte.rang() == rare && proche(correcte.position(), 0), "Correcte de score 30 : tout en bas de sa fourchette (aucun saut avec la Brute de 29)");
        Qualite.Resultat chef = exacte.calculer(90, rare, 0, 0, h);
        verifier(chef.rang() == rare && proche(chef.position(), 60 / 70.0), "score 90 : 86 % de la fourchette");
        Qualite.Resultat monte = exacte.calculer(100, epique, 1, 0, h);
        verifier(monte.rang() == legendaire && proche(monte.position(), 0.3), "Chef-d'œuvre Épique qui gagne un rang : 30 % de la fourchette Légendaire");
        verifier(exacte.calculer(100, legendaire, 1, 0, h).rang() == legendaire, "un Légendaire ne devient jamais Mythique par le grade");
        verifier(exacte.calculer(84, rare, 1, 0, h).rang() == rare, "une Excellente ne gagne pas de rang");
        // rangs hauts : il faut la main
        Qualite.Resultat sous = exacte.calculer(69, legendaire, 0, 0, h);
        verifier(sous.rang() == epique && proche(sous.position(), 69 / 70.0), "Légendaire sous 70 : sort Épique, en haut de sa fourchette");
        verifier(exacte.calculer(70, legendaire, 0, 0, h).rang() == legendaire, "Légendaire à 70 : gardé");
        verifier(exacte.calculer(72, legendaire, 0, 10, h).rang() == epique, "le score exigé monte de 0,5 point par étage d'écart");
        verifier(exacte.calculer(84, divin, 0, 0, h).rang().id().equals("mythique"), "Divin sous 85 : sort Mythique");

        // avec l'aléa : la place reste dans [0 ; 1]
        Random h2 = new Random(42);
        boolean dedans = true;
        for (int i = 0; i < 2000; i++) {
            double p = q.calculer(h2.nextDouble() * 100, rare, 0.33, 0, h2).position();
            if (p < 0 || p > 1) dedans = false;
        }
        verifier(dedans, "la place dans la fourchette reste entre 0 et 1");
        verifier(proche(Qualite.reflechir(-0.2), 0.2) && proche(Qualite.reflechir(1.3), 0.7) && proche(Qualite.reflechir(0.4), 0.4), "aléa rabattu aux bords");

        // bouton : jamais mieux que Fine, environ 45 % de Brutes
        int brutes = 0, mieux = 0;
        for (int i = 0; i < 20000; i++) {
            Qualite.Grade g = q.grade(q.scoreBouton(h2));
            if (g == Qualite.Grade.BRUTE) brutes++;
            if (g.ordinal() > Qualite.Grade.FINE.ordinal()) mieux++;
        }
        verifier(mieux == 0, "le bouton ne fait jamais mieux que Fine");
        verifier(Math.abs(brutes / 20000.0 - 0.45) < 0.02, "le bouton donne environ 45 % de Brutes (" + brutes / 200.0 + " %)");
    }

    // ------------------------------------------------------------------ formule de dégâts

    private static void formule() {
        Map<String, Double> joueur = Map.of("force", 50.0, "intelligence", 50.0, "agilite", 20.0);
        double epee = Formule.puissance(30, Map.of("force", "B", "agilite", "D"), s -> joueur.getOrDefault(s, 0.0), 0, 1);
        verifier(proche(epee, 90), "épée 30 d'Attaque, FOR B, AGI D : puissance 90");
        double degats = Formule.degats(epee, 50);
        verifier(Formule.arrondir(degats) == 58 && Formule.arrondir(degats * 1.25) == 72, "contre 50 de défense : 58 dégâts, 72 en critique");
        double baton = Formule.puissance(30, Map.of("intelligence", "A", "agilite", "E"), s -> joueur.getOrDefault(s, 0.0), 0, 1);
        verifier(proche(baton, 95) && Formule.arrondir(Formule.degats(baton, 20)) == 78, "bâton 30 de Puissance, INT A, AGI E : puissance 95, 78 dégâts contre 20");
        verifier(proche(Formule.degats(60, 60), 30) && proche(Formule.degats(60, 120), 20) && proche(Formule.degats(60, 20), 45), "part des dégâts : 50 %, 33 %, 75 %");
        verifier(Formule.degats(0, 10) == 0 && Formule.arrondir(0.2) == 1, "pas de puissance, pas de dégâts ; un coup qui porte fait au moins 1");
        verifier(proche(TypeObjet.coefficient("S"), 1.5) && proche(TypeObjet.coefficient("e"), 0.25) && proche(TypeObjet.coefficient("0,8"), 0.8), "lettres de scale");
        verifier(proche(Formule.charge(0, 1.6), 0.2 + 0.8 * Math.pow(0.5 / 12.5, 2)) && proche(Formule.charge(40, 1.6), 1), "recharge de l'arme entre deux coups");
        verifier(proche(Formule.puissance(10, Map.of(), s -> 0, 5, 2), 30), "bonus de dégâts et multiplicateur de compétence");
    }

    // ------------------------------------------------------------------ fichiers d'exemple

    private static void catalogue(Catalogue c, List<String> problemes) {
        verifier(problemes.isEmpty(), "les fichiers d'exemple se chargent sans remarque : " + problemes);
        verifier(c.raretes().tous().size() == 7, "raretes.yml : 7 rangs");
        verifier(c.modificateurs().tous().size() >= 42, "modificateurs.yml : les 4 stats d'objet et les 38 modificateurs (" + c.modificateurs().tous().size() + ")");
        verifier(c.modificateurs().id("armure").equals("armor") && c.modificateurs().id("Feu").equals("fire-damage") && c.modificateurs().id("critique").equals("critical-strike-chance"), "alias en français");
        verifier(c.modificateurs().afficher("critical-strike-chance", 5).equals("+5 % Chance de coup critique") && c.modificateurs().afficher("armor", -2).equals("−2 Armure physique"), "affichage des modificateurs");
        ModeleObjet epee = c.modele("epee");
        verifier(epee != null && epee.kit() != null && epee.kit().emplacements().equals(List.of("lame", "garde", "poignee", "pommeau")), "épée modulaire à 4 emplacements");
        verifier(c.pieces("epee", "lame").size() == 6 && c.pieces("epee", "garde").size() == 6 && c.pieces("epee", "poignee").size() == 3 && c.pieces("epee", "pommeau").size() == 3,
            "6 lames, 6 gardes, 3 poignées et 3 pommeaux : 324 épées possibles");
        ModeleObjet lame = c.modele("lame_du_veilleur");
        verifier(lame != null && lame.rang().id().equals("legendaire") && lame.niveau() == 40 && lame.classes().equals(List.of("guerrier", "spadassin")), "conditions de la Lame du Veilleur");
        // 62 d'attaque au rang Légendaire = 62 / 1,5 au rang Commun
        verifier(lame != null && proche(lame.stats().get("attaque"), 62 / 1.5), "les stats du fichier sont celles du rang de base du modèle");
        ModeleObjet baton = c.modele("baton_d_apprenti");
        verifier(baton != null && baton.magique(), "bâton magique");
        verifier(c.modele("rubis_taille").empilable() && !c.modele("dague_de_chasse").empilable(), "les gemmes s'empilent, pas les armes");
        verifier(c.modele("dague_de_chasse").recette().materiaux().equals(Map.of("iron_ingot", 1, "leather", 1, "stick", 1)), "recette de la dague");
        verifier(c.materiau("acier").palier() == 2 && c.materiau("fer").palier() == 1, "paliers des matériaux");
    }

    private static Exemplaire epeeMixte() {
        Exemplaire ex = new Exemplaire();
        ex.modele = "epee";
        ex.rang = "commun";
        ex.pieces.put("lame", "fer/lame_droite");
        ex.pieces.put("garde", "acier/garde_croissant");
        ex.pieces.put("poignee", "cuivre/poignee_cuir");
        ex.pieces.put("pommeau", "fer/pommeau_rond");
        return ex;
    }

    private static void calcul(Catalogue c) {
        ModeleObjet epee = c.modele("epee");
        Exemplaire ex = epeeMixte();
        double reference = 15 + 3 * 1.12 + 1 + 1;
        verifier(proche(Calcul.references(c, epee, ex).get("attaque"), reference), "référence d'une épée : somme des pièces, chacune avec le palier de son matériau");
        Map<String, Double> stats = Calcul.stats(c, ex, 0.03);
        verifier(proche(stats.get("attaque"), Math.round(reference)), "en bas de la fourchette Commune : la référence");
        verifier(stats.get("critical-strike-chance") == null, "les pièces d'exemple n'ont plus de modificateur fixe");
        verifier(proche(c.poidsBonus(epee, ex).getOrDefault("physical-damage", 0.0), 3) && c.poidsBonus(epee, ex).getOrDefault("attack-speed", 1.0) == 1.0,
            "lignes bonus orientées par le matériau de la lame (fer : dégâts physiques plus souvent)");
        ModeleObjet casque = c.modele("casque_acier"), cuirasse = c.modele("plastron_acier");
        verifier(cuirasse != null && cuirasse.apparence().equipement() != null && proche(c.poidsBonus(cuirasse, null).getOrDefault("damage-reduction", 0.0), 3),
            "armure d'exemple : dessin porté et matériau déclaré");
        verifier(casque != null && casque.apparence().equipement() == null && "exemple/armure/casque_acier".equals(casque.apparence().modele()),
            "casque d'exemple : un modèle 3D porté sur la tête, sans dessin d'armure");
        ex.position = 1;
        verifier(proche(Calcul.stats(c, ex, 0.03).get("attaque"), Math.round(reference * 1.1)), "en haut de la fourchette Commune : le bas de la Peu commune");
        ex.rang = "rare";
        ex.position = 0;
        ex.amelioration = 5;
        verifier(proche(Calcul.stats(c, ex, 0.03).get("attaque"), Math.round(reference * 1.2 * 1.15)), "rang Rare et amélioration +5 (+15 %)");
        ex.amelioration = 0;
        ex.bonus.put("physical-damage", 3.0);
        ex.gemmes.add("rubis_taille");
        verifier(proche(Calcul.stats(c, ex, 0.03).get("physical-damage"), 7), "ligne bonus et gemme additionnées");
        verifier(Calcul.durabiliteOrigine(c, epee, ex) == 140 + 25 + 20 + 15, "durabilité : somme des pièces");
        verifier(Calcul.emplacementsGemmes(c, epee, ex) == 1 && Calcul.emplacementsGemmes(c, c.modele("lame_du_veilleur"), ex) == 2, "emplacements de gemmes : ceux du rang, ou ceux du modèle");
        double[] f = Calcul.fourchette(c, epee, ex, "attaque", c.raretes().parId("rare"), false);
        verifier(f != null && proche(f[0], reference * 1.2) && proche(f[1], reference * 1.35), "fourchette Rare de l'épée");
        ex.rebut = true;
        ex.rang = "commun";
        ex.position = 0;
        ex.bonus.clear();
        ex.gemmes.clear();
        verifier(proche(Calcul.stats(c, ex, 0.03).get("attaque"), Math.round(reference * 0.85)), "rebut : 85 % de la référence au plus bas");
    }

    private static void description(Catalogue c) {
        ModeleObjet epee = c.modele("epee");
        Exemplaire ex = epeeMixte();
        ex.rang = "rare";
        ex.grade = "fine";
        ex.fabricant = "Antoine";
        ex.bonus.put("lifesteal", 1.2);
        ex.gemmes.add("rubis_taille");
        ex.durabiliteOrigine = ex.durabiliteMax = 200;
        ex.durabilite = 150;
        ex.amelioration = 2;
        verifier(Description.nom(c, epee, ex).equals("<#4d8dff>Épée de fer <white>+2"), "nom : couleur du rang, matériau de la lame, amélioration (" + Description.nom(c, epee, ex) + ")");
        List<String> l = Description.lignes(c, epee, ex, Calcul.stats(c, ex, 0.03), id -> id.equals("entaille") ? "Entaille (6 s)" : null);
        String tout = String.join("\n", l);
        verifier(l.get(0).contains("◆ Rare") && l.get(0).contains("Épée"), "première ligne : rang et type");
        verifier(tout.contains("Qualité : ") && tout.contains("Fine"), "qualité");
        verifier(tout.contains("Attaque physique <white>" + Textes.nombre(Math.round((15 + 3 * 1.12 + 2) * 1.2 * 1.06))), "attaque avec le rang et l'amélioration");
        verifier(tout.contains("Scale <white>FOR B") && tout.contains("AGI D") && tout.contains("Type <white>Cutting"), "scale et type d'attaque");
        verifier(tout.contains("◇ +1,2 % Vol de vie"), "ligne bonus");
        verifier(tout.contains("❖ Rubis taillé") && tout.contains("+4 Dégâts physiques"), "gemme sertie");
        verifier(tout.contains("Durabilité : <white>150") && tout.contains("Forgé par") && tout.contains("Antoine"), "durabilité et fabricant");
        verifier(!l.get(0).isEmpty() && !l.get(l.size() - 1).isEmpty() && !tout.contains("\n\n\n"), "pas de ligne vide en trop");
        ex.durabilite = 0;
        verifier(String.join("\n", Description.lignes(c, epee, ex, Map.of(), null)).contains("Cassé"), "objet cassé");
        ex.nom = "Croc <red>du Nord";
        verifier(Description.nom(c, epee, ex).contains("Croc \\<red>du Nord"), "un nom donné par un joueur ne peut pas contenir de balises");
        ModeleObjet rubis = c.modele("rubis_taille");
        Exemplaire g = new Exemplaire();
        g.modele = "rubis_taille";
        g.rang = "rare";
        verifier(String.join("\n", Description.lignes(c, rubis, g, Map.of(), null)).contains("Une fois sertie"), "description d'une gemme");
    }

    // ------------------------------------------------------------------ pack

    private static void pack(Catalogue c, Path dossier) throws IOException {
        List<String> problemes = new ArrayList<>();
        Map<String, byte[]> fichiers = GenerateurPack.generer(c, dossier.resolve("ressources"), problemes);
        verifier(problemes.isEmpty(), "le pack d'exemple se construit sans remarque : " + problemes);
        byte[] def = fichiers.get("assets/voraceitem/items/kit/epee.json");
        verifier(def != null, "définition de l'épée modulaire");
        if (def != null) {
            JsonObject modele = JsonParser.parseString(new String(def, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("model");
            JsonArray couches = modele.getAsJsonArray("models");
            verifier(modele.get("type").getAsString().equals("minecraft:composite") && couches.size() == 4, "modèle composé de 4 couches");
            JsonObject lames = couches.get(0).getAsJsonObject();
            verifier(lames.get("type").getAsString().equals("minecraft:select") && lames.get("property").getAsString().equals("minecraft:custom_model_data")
                && lames.get("index").getAsInt() == 0 && lames.getAsJsonArray("cases").size() == 6
                && lames.getAsJsonObject("fallback").get("type").getAsString().equals("minecraft:empty"), "la couche des lames choisit parmi 6 lames, rien par défaut");
            verifier(couches.get(3).getAsJsonObject().get("index").getAsInt() == 3 && couches.get(3).getAsJsonObject().getAsJsonArray("cases").size() == 3, "la couche des pommeaux lit la 4e chaîne");
            JsonObject cas = lames.getAsJsonArray("cases").get(0).getAsJsonObject();
            String piece = cas.get("when").getAsString();
            verifier(cas.getAsJsonObject("model").get("model").getAsString().equals("voraceitem:item/piece/" + piece), "chaque cas renvoie au modèle de sa pièce");
        }
        byte[] lame = fichiers.get("assets/voraceitem/models/item/piece/fer/lame_droite.json");
        verifier(lame != null, "modèle de la lame de fer");
        if (lame != null) {
            JsonObject m = JsonParser.parseString(new String(lame, StandardCharsets.UTF_8)).getAsJsonObject();
            verifier(m.getAsJsonObject("textures").get("0").getAsString().equals("voraceitem:item/exemple/fer/lame_droite"), "sa texture est reliée à l'espace de noms voraceitem");
            verifier(m.has("display") && m.getAsJsonObject("display").has("thirdperson_righthand") && m.getAsJsonObject("display").has("gui"), "elle reçoit les réglages d'affichage du kit");
        }
        verifier(fichiers.containsKey("assets/voraceitem/textures/item/exemple/fer/lame_droite.png"), "texture de la lame copiée");
        verifier(fichiers.size() == 1 + 18 + 18 + 18 + 18 + 12 * 3 + 3 * 3, "épée (73 fichiers) et 12 armures d'exemple (icône ou texture du casque 3D, modèle, définition) avec leurs 3 dessins portés (" + fichiers.size() + " fichiers)");
        verifier(fichiers.containsKey("assets/voraceitem/equipment/exemple/armure_acier.json")
            && fichiers.containsKey("assets/voraceitem/textures/entity/equipment/humanoid/exemple/armure_acier.png")
            && fichiers.containsKey("assets/voraceitem/textures/entity/equipment/humanoid_leggings/exemple/armure_acier.png"), "dessin d'armure porté écrit dans le pack (1.21.4)");
        byte[] seule = fichiers.get("assets/voraceitem/models/item/piece_seule/fer/pommeau_rond.json");
        verifier(seule != null && fichiers.containsKey("assets/voraceitem/items/piece/fer/pommeau_rond.json"), "le pommeau a son objet à lui");
        if (seule != null) {
            JsonObject m = JsonParser.parseString(new String(seule, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject vue = m.getAsJsonObject("display").getAsJsonObject("gui");
            // pommeau de fer : x de 7,625 à 8,5, y de 1,625 à 2,25 ; agrandi 4 fois (le maximum), ramené au milieu de la case
            verifier(m.get("parent").getAsString().equals("voraceitem:item/piece/fer/pommeau_rond")
                && proche(vue.getAsJsonArray("scale").get(0).getAsDouble(), 4)
                && vue.getAsJsonArray("translation").get(1).getAsDouble() > 20, "seul, le pommeau est agrandi et recentré dans sa case");
        }

        // écriture : rien ne change au second passage, un fichier en trop est retiré
        Path sortie = dossier.resolve("pack-genere");
        verifier(GenerateurPack.ecrire(sortie, fichiers), "première écriture : le dossier change");
        verifier(!GenerateurPack.ecrire(sortie, fichiers), "seconde écriture : rien ne change");
        Files.writeString(sortie.resolve("assets/voraceitem/items/kit/ancien.json"), "{}");
        verifier(GenerateurPack.ecrire(sortie, fichiers) && !Files.exists(sortie.resolve("assets/voraceitem/items/kit/ancien.json")), "un fichier qui n'est plus dans le pack est retiré");
    }

    private static void ingredients() {
        Map<String, Integer> total = Ingredients.somme(List.of(Map.of("iron_ingot", 2), Map.of("iron_ingot", 1, "leather", 1)), 1);
        verifier(total.equals(Map.of("iron_ingot", 3, "leather", 1)), "les coûts des pièces s'additionnent");
        verifier(Ingredients.somme(List.of(Map.of("iron_ingot", 3, "gem", 1)), 1.25).equals(Map.of("iron_ingot", 4, "gem", 1)), "surcoût de 25 %, arrondi au plus proche");
    }

    // ------------------------------------------------------------------ outils

    /** Copie dans un dossier temporaire les fichiers que le plugin installe au premier lancement. */
    private static Path installerExemples() throws IOException {
        Path d = Files.createTempDirectory("vorace-item-tests");
        for (String f : List.of("raretes.yml", "modificateurs.yml", "types.yml", "competences.yml", "butins.yml")) copier(f, d.resolve(f));
        copier("exemples/objets/exemples.yml", d.resolve("objets/exemples.yml"));
        copier("exemples/materiaux/exemples.yml", d.resolve("materiaux/exemples.yml"));
        try (InputStream index = Tests.class.getResourceAsStream("/exemples/ressources.txt")) {
            if (index == null) throw new IOException("exemples/ressources.txt introuvable");
            for (String l : new String(index.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!l.isBlank()) copier("exemples/ressources/" + l.trim(), d.resolve("ressources").resolve(l.trim()));
            }
        }
        return d;
    }

    private static void copier(String ressource, Path cible) throws IOException {
        try (InputStream in = Tests.class.getResourceAsStream("/" + ressource)) {
            if (in == null) throw new IOException("ressource introuvable : " + ressource);
            Files.createDirectories(cible.getParent());
            Files.copy(in, cible);
        }
    }

    private static void supprimer(Path d) throws IOException {
        try (Stream<Path> s = Files.walk(d)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}

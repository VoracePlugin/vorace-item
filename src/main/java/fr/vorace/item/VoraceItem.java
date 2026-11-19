package fr.vorace.item;

import fr.vorace.Vorace;
import fr.vorace.item.api.ProfilJoueur;
import fr.vorace.item.api.VoraceItemAPI;
import fr.vorace.item.definitions.Catalogue;
import fr.vorace.item.equipement.Equipement;
import fr.vorace.item.equipement.ProfilParDefaut;
import fr.vorace.item.equipement.Usure;
import fr.vorace.item.exemplaire.Exemplaires;
import fr.vorace.item.exemplaire.Qualite;
import fr.vorace.item.interfaces.EcouteurMenus;
import fr.vorace.item.pack.GenerateurPack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * vorace-item : création des objets RPG (stats, rangs, qualité, armes modulaires, durabilité), appuyée sur vorace-core
 * pour le pack de ressources et l'apparence des objets. Le combat, les compétences, la fabrication et le butin sont
 * gérés par d'autres plugins, qui créent les objets et lisent leurs stats avec {@link VoraceItemAPI}.
 */
public final class VoraceItem extends JavaPlugin {

    /** Fichiers créés au premier lancement (ils ne sont jamais remplacés ensuite). */
    private static final List<String> FICHIERS = List.of("raretes.yml", "modificateurs.yml", "types.yml");

    /** Exemples installés une seule fois : ressource du .jar → fichier dans plugins/VoraceItem. */
    private static final List<String> EXEMPLES = List.of("objets/exemples.yml", "materiaux/exemples.yml");

    /**
     * Lot d'exemples ajouté après coup : installé une fois sur un serveur déjà lancé (donnees/exemples.txt garde la
     * liste des lots posés). Son fichier d'objets (ou null), les fichiers d'une version précédente à remplacer s'ils
     * n'ont pas été retouchés (chemin dans le dossier du plugin → empreinte SHA-256) et les ressources à copier
     * (débuts de chemin dans ressources/). Aucun autre fichier existant n'est remplacé.
     */
    private record Lot(String fichier, Map<String, String> anciens, String... ressources) {}

    private static final java.util.LinkedHashMap<String, Lot> LOTS = new java.util.LinkedHashMap<>();
    static {
        LOTS.put("poings", new Lot("objets/poings.yml", Map.of(), "textures/exemple/poing/"));
        // gantelets en 3D : poings.yml de la 0.14.1 remplacé s'il n'a pas été retouché
        LOTS.put("poings-3d", new Lot("objets/poings.yml",
            Map.of("objets/poings.yml", "c3043f43a601e28af53e153f952f6aaf2cd57353550d8c5f59fc435b647a5c46"),
            "modeles/exemple/poing/", "textures/exemple/poing/gantelets_3d"));
        // gantelets redessinés (plus grands que la manche d'un plastron, main gauche à part) : ceux de la 0.14.2 remplacés
        LOTS.put("poings-3d-2", new Lot(null, Map.of(
                "ressources/modeles/exemple/poing/gantelets.json", "ed7ed7d615398a073f862d9eca7ebe5b1d9e3c63ac708b979caa5bb7fb86e898",
                "ressources/textures/exemple/poing/gantelets_3d.png", "05c2942bd121ddf0c35fa2a93143edc2f029be19b661e40b0d725c007f477af3"),
            "modeles/exemple/poing/", "textures/exemple/poing/gantelets_3d"));
        // gantelets affinés (juste au-dessus de la manche d'un plastron) : ceux de la 0.14.3 remplacés
        LOTS.put("poings-3d-3", new Lot(null, Map.of(
                "ressources/modeles/exemple/poing/gantelets.json", "fa7082798f292adc118d13ec209dcfb482dd0c4f4b8afac81e6b249a53385a31",
                "ressources/modeles/exemple/poing/gantelets_gauche.json", "c0d5d8425b9508295a9e020a05a3082ed389d571223e0ba7bf25cfa8006dbfcc",
                "ressources/textures/exemple/poing/gantelets_3d.png", "0acf4fd3bf81091987f7b01bdac1185bf76c7eb0743868b0511b87327945094c"),
            "modeles/exemple/poing/", "textures/exemple/poing/gantelets_3d"));
        // les cinq armes de poing en 3D (gantelets plus détaillés) : poings.yml et gantelets de la 0.14.4 remplacés
        LOTS.put("poings-3d-4", new Lot("objets/poings.yml", Map.of(
                "objets/poings.yml", "1f2dadd03d501f5306d21fdfc441727236b4bab101efd586f747af323b2937a9",
                "ressources/modeles/exemple/poing/gantelets.json", "d0d982e81161e5b463b83cfffc8b1e69f54b352ac8a7e8251f2127e347fc575c",
                "ressources/modeles/exemple/poing/gantelets_gauche.json", "cb1175ec4919b8d32633759e640027bda748997cf300a1f106f0bd4b862a551b",
                "ressources/textures/exemple/poing/gantelets_3d.png", "dce610b85f1b04610f6ae537a7c028a694e1dbdcef1b62211ea20bfc0696792f"),
            "modeles/exemple/poing/", "textures/exemple/poing/"));
        // armes de poing redessinées (silhouettes, plaques évasées, spirales, motifs) : celles de la 0.14.5 remplacées
        LOTS.put("poings-3d-5", new Lot(null, Map.ofEntries(
            Map.entry("ressources/modeles/exemple/poing/gantelets.json", "9cde684bf70bccd60a20afd440d4d6309d3e5217f84494a564432e0d6d3cc734"),
            Map.entry("ressources/modeles/exemple/poing/gantelets_gauche.json", "523e6557c05b0ccd5339d20790ca6c7a73ffbfc8cd67ad79019f588ccf84b91f"),
            Map.entry("ressources/textures/exemple/poing/gantelets_3d.png", "d6fa0e4e73f04484e3f65f0410a17a99098e8b3ea244381419b897f0c7c55dc3"),
            Map.entry("ressources/modeles/exemple/poing/bandes_de_combat.json", "4123cf44463a50eb1690a727160edcb0f324636921f4cb565cb21863a93f4cc2"),
            Map.entry("ressources/modeles/exemple/poing/bandes_de_combat_gauche.json", "bc9137d6bef7355174b0e18b0071d9a5ad49a69351259082939f1b0080b559a9"),
            Map.entry("ressources/textures/exemple/poing/bandes_de_combat_3d.png", "e23b52883cfa3b9be9dd247bf2f0a2ef4a0c3e45d508381db1caf0d0c6755711"),
            Map.entry("ressources/modeles/exemple/poing/cestes.json", "b51f83506026037e9fef4c98611dba0133ec997ea4d21cc675e98063e4004371"),
            Map.entry("ressources/modeles/exemple/poing/cestes_gauche.json", "c323064a92a6644319c17adf8f481cce4464e3b68849399fa4c6c1d083afe463"),
            Map.entry("ressources/textures/exemple/poing/cestes_3d.png", "6c90643a53b81dcbc1d1a6d74046d8a3665ae5af94b4db1aaa27fe42d1c728bc"),
            Map.entry("ressources/modeles/exemple/poing/brassards_de_garde.json", "fee4da0611ffa293d6167598d4a2c3eb4b0bd884a7a29cefea3708583e9b425d"),
            Map.entry("ressources/modeles/exemple/poing/brassards_de_garde_gauche.json", "329b90bd0646dc21a3d700fb3718564f0738aadc85e6cca87e0b97d83261612d"),
            Map.entry("ressources/textures/exemple/poing/brassards_de_garde_3d.png", "eefe660c6f1470406ebf336adddeedbbede89e20663cf203b972e63e05b1af67"),
            Map.entry("ressources/modeles/exemple/poing/bandes_rituelles.json", "e809b9a5226aa7e9ffcc04a5390b9ab1a15cb7c6f0ab4bc58e770cc8b0c83f8b"),
            Map.entry("ressources/modeles/exemple/poing/bandes_rituelles_gauche.json", "3107005afc194558a6939b43b60c8f0194ec50a8b4a4bcc944b6ed59e466649e"),
            Map.entry("ressources/textures/exemple/poing/bandes_rituelles_3d.png", "55b7607121f7abd8b7901bb3738a5d58107d2440edabe35a7eef4fa030a16abb")),
            "modeles/exemple/poing/", "textures/exemple/poing/"));
        // cestes en vrai poing américain : ceux de la 0.14.7 remplacés
        LOTS.put("poings-3d-6", new Lot(null, Map.ofEntries(
            Map.entry("ressources/modeles/exemple/poing/cestes.json", "a68805e14e731ace961b3a3c9a2b73e5daf3cc3d01081f6000c6dacf55810fb1"),
            Map.entry("ressources/modeles/exemple/poing/cestes_gauche.json", "4d2d610b72e1978d1976e93ac09ead3ce57d8e8da3f37cee7e623de4a131fa5f"),
            Map.entry("ressources/textures/exemple/poing/cestes_3d.png", "ed43b076123b92a5c3a096eb81fb8d756b1cd47680f6102cb3e9c604fe097c24")),
            "modeles/exemple/poing/cestes", "textures/exemple/poing/cestes_3d"));
        // première personne posée sur le bras du joueur : modèles de la 0.14.8 remplacés
        LOTS.put("poings-3d-7", new Lot(null, Map.ofEntries(
            Map.entry("ressources/modeles/exemple/poing/gantelets.json", "d6d3da68b70ef9c9ba543412b691c1bdecadca38a0bf5e6b910187a0e68245bf"),
            Map.entry("ressources/modeles/exemple/poing/gantelets_gauche.json", "1a7cafd42e7b4731fa0d2bb2c2f46cff3d8ecaeee276e4cb0e7c7fcd9962f120"),
            Map.entry("ressources/modeles/exemple/poing/bandes_de_combat.json", "715cff11d25e5d354cd0eef6a74484bc2b72aedc1a033f6ad5cf285a8a85d2c4"),
            Map.entry("ressources/modeles/exemple/poing/bandes_de_combat_gauche.json", "a1231b0f13eb3878160a9eb60cb998921f2e2a8cfd4f96967bd6f99be1728c0d"),
            Map.entry("ressources/modeles/exemple/poing/cestes.json", "34575b3775d7eefeb94a6d02c14546b25153ac2710714623be44dbfe869bbca4"),
            Map.entry("ressources/modeles/exemple/poing/cestes_gauche.json", "9071816b34d73408cab75a336fd6e67ce1dca5b403945c0b11975fd836c4e9da"),
            Map.entry("ressources/modeles/exemple/poing/brassards_de_garde.json", "e4d20ffe56bdd4981765b52ab230d61d9418943b95c9543426aeafcd1a0fc507"),
            Map.entry("ressources/modeles/exemple/poing/brassards_de_garde_gauche.json", "193ca59083f633a95d70b23fec9e11ff204b85b1cf753fced6f8143eaf8f20ba"),
            Map.entry("ressources/modeles/exemple/poing/bandes_rituelles.json", "8a8a8d46e9ec0cc451b4967a8852ea8522774a0ee73e7a97747844bb65d76690"),
            Map.entry("ressources/modeles/exemple/poing/bandes_rituelles_gauche.json", "8398a24d455b2f70ccd1b66ffd71276b3d4800057d894b0581f77634d94e9d87")),
            "modeles/exemple/poing/"));
        // gantelets et brassards raccourcis : ceux de la 0.14.9 remplacés
        LOTS.put("poings-3d-8", new Lot(null, Map.ofEntries(
            Map.entry("ressources/modeles/exemple/poing/gantelets.json", "215803e22050f593bab96d75537bbaca5b8b144ce62d33839157002cc7d483c3"),
            Map.entry("ressources/modeles/exemple/poing/gantelets_gauche.json", "4d0a741cc47900c061a5b76fec0921dfde2c2760b0c8e6ff3a551cb58cfc5cb2"),
            Map.entry("ressources/textures/exemple/poing/gantelets_3d.png", "7e494b7526cf3cc684b0ee1d13bd215b7ab88edaebd2e1fa17648c8ae2761331"),
            Map.entry("ressources/modeles/exemple/poing/brassards_de_garde.json", "328c9f5c2c35c16b25e00f567279c719482346f2af646ddee5de53ee0112e491"),
            Map.entry("ressources/modeles/exemple/poing/brassards_de_garde_gauche.json", "2b24913c53c9355e0148acfdfc2e1b3c12f7bcd2b5654704e6a18c0beef81d92"),
            Map.entry("ressources/textures/exemple/poing/brassards_de_garde_3d.png", "a603908397074827fa291c3dd127ca414ebeb0a7ea1426e7e1e97465b187b8ea")),
            "modeles/exemple/poing/gantelets", "modeles/exemple/poing/brassards", "textures/exemple/poing/gantelets_3d",
            "textures/exemple/poing/brassards_de_garde_3d"));
    }

    private Vorace core;
    private Reglages reglages;
    private volatile Catalogue catalogue;
    private Qualite qualite;
    private Exemplaires exemplaires;
    private fr.vorace.item.exemplaire.Pieces pieces;
    private Equipement equipement;
    private ProfilParDefaut profilParDefaut;
    private Usure usure;

    /** Le plugin vorace-core (objets, pack, modèles). */
    public Vorace core() { return core; }
    public Reglages reglages() { return reglages; }
    public Catalogue catalogue() { return catalogue; }
    public Qualite qualite() { return qualite; }
    public Exemplaires exemplaires() { return exemplaires; }
    public fr.vorace.item.exemplaire.Pieces pieces() { return pieces; }
    public Equipement equipement() { return equipement; }
    public Usure usure() { return usure; }
    public ProfilParDefaut profilParDefaut() { return profilParDefaut; }

    /** Niveau et classes des joueurs (conditions des objets) : ceux du plugin des classes s'il en fournit, sinon le profil par défaut. */
    public ProfilJoueur profil() {
        ProfilJoueur p = Bukkit.getServicesManager().load(ProfilJoueur.class);
        return p == null ? profilParDefaut : p;
    }

    @Override
    public void onEnable() {
        if (!(Bukkit.getPluginManager().getPlugin("Vorace") instanceof Vorace v) || !v.isEnabled()) {
            getLogger().severe("vorace-item a besoin de vorace-core (plugin « Vorace ») : désactivé.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        core = v;
        exemplaires = new Exemplaires(this);
        pieces = new fr.vorace.item.exemplaire.Pieces(this);
        equipement = new Equipement(this);
        profilParDefaut = new ProfilParDefaut(this);
        usure = new Usure(this);

        installerFichiers();
        charger();

        Bukkit.getPluginManager().registerEvents(new EcouteurObjets(this), this);
        Bukkit.getPluginManager().registerEvents(new EcouteurMenus(this), this);
        Bukkit.getScheduler().runTaskTimer(this, () -> { for (Player p : Bukkit.getOnlinePlayers()) equipement.appliquerAttributs(p); }, 10L, 10L);

        Commande commande = new Commande(this);
        var c = getCommand("vitem");
        if (c != null) { c.setExecutor(commande); c.setTabCompleter(commande); }
        VoraceItemAPI.initialiser(this);
        getLogger().info("vorace-item " + getPluginMeta().getVersion() + " : " + catalogue.modeles().size() + " modèle(s), "
            + catalogue.pieces().size() + " pièce(s) dans " + catalogue.materiaux().size() + " matériau(x).");
    }

    @Override
    public void onDisable() {
        // menus ouverts (assemblage, réparation...) : les objets posés dans leurs cases sont rendus avant l'arrêt
        for (Player p : Bukkit.getOnlinePlayers()) {
            org.bukkit.inventory.Inventory haut = p.getOpenInventory().getTopInventory();
            if (!(haut.getHolder() instanceof fr.vorace.item.interfaces.Menu menu)) continue;
            for (int libre : menu.libres()) {
                org.bukkit.inventory.ItemStack o = haut.getItem(libre);
                if (o == null || o.getType().isAir()) continue;
                haut.setItem(libre, null);
                fr.vorace.item.interfaces.EcouteurMenus.rendre(p, o);
            }
            p.closeInventory();
        }
        if (equipement == null) return;
        // arrêt du serveur : les modificateurs d'attributs restent sur les joueurs (sinon un joueur qui avait de la vie
        // en plus grâce à son équipement la perdrait au chargement de sa sauvegarde) ; plugin arrêté seul : on les retire
        boolean arretServeur = Bukkit.isStopping();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!arretServeur) equipement.retirerAttributs(p);
            equipement.oublier(p);
        }
        VoraceItemAPI.initialiser(null);
    }

    // ------------------------------------------------------------------ chargement

    /** (Re)lit tous les fichiers, refait la partie du pack de vorace-item et l'affichage des objets des joueurs connectés. */
    public List<String> charger() {
        saveDefaultConfig();
        reloadConfig();
        reglages = new Reglages(getConfig());
        List<String> problemes = new ArrayList<>();
        Path dossier = getDataFolder().toPath();
        catalogue = Catalogue.charger(dossier, problemes, typesParDefaut());
        qualite = Qualite.lire(catalogue.raretes(), getConfig().getConfigurationSection("qualite"));
        equipement.toutOublier();
        genererPack(problemes);
        for (Player p : Bukkit.getOnlinePlayers()) rafraichir(p);
        problemes.forEach(m -> getLogger().warning(m));
        return problemes;
    }

    /** Écrit la partie du pack de vorace-item dans le dossier que vorace-core fusionne, et fait reconstruire le pack si elle a changé. */
    public boolean genererPack(List<String> problemes) {
        try {
            Map<String, byte[]> fichiers = GenerateurPack.generer(catalogue, getDataFolder().toPath().resolve("ressources"), problemes);
            Path dossier = core.packs().racineServeur().resolve(reglages.dossierPack).normalize();
            boolean change = GenerateurPack.ecrire(dossier, fichiers);
            if (change) {
                getLogger().info("Pack : " + fichiers.size() + " fichier(s) écrits dans " + reglages.dossierPack + ", vorace-core reconstruit le pack.");
                core.reconstruire(null);
            }
            return change;
        } catch (IOException | RuntimeException e) {
            getLogger().warning("Impossible d'écrire la partie du pack de vorace-item : " + e);
            return false;
        }
    }

    private void installerFichiers() {
        Path dossier = getDataFolder().toPath();
        try {
            Files.createDirectories(dossier.resolve("objets"));
            Files.createDirectories(dossier.resolve("materiaux"));
            Files.createDirectories(dossier.resolve("ressources/modeles"));
            Files.createDirectories(dossier.resolve("ressources/textures"));
            for (String f : FICHIERS) copier(f, dossier.resolve(f));
            saveDefaultConfig();
            reloadConfig();
            Path liste = dossier.resolve("donnees/exemples.txt");
            if (!getConfig().getBoolean("exemples", true)) return;
            List<String> poses = Files.isRegularFile(liste) ? new java.util.ArrayList<>(Files.readAllLines(liste)) : new java.util.ArrayList<>();
            poses.replaceAll(String::trim);
            boolean premier = !poses.contains("exemples");
            List<String> ajoutes = new java.util.ArrayList<>();
            if (premier) {
                for (String f : EXEMPLES) copier("exemples/" + f, dossier.resolve(f));
                ajoutes.add("exemples");
            }
            for (var lot : LOTS.entrySet()) {
                if (poses.contains(lot.getKey())) continue;
                if (!premier) {
                    for (Map.Entry<String, String> ancien : lot.getValue().anciens().entrySet()) {
                        Path f = dossier.resolve(ancien.getKey());
                        if (Files.isRegularFile(f) && ancien.getValue().equals(empreinte(Files.readAllBytes(f)))) Files.delete(f);
                    }
                }
                if (lot.getValue().fichier() != null) copier("exemples/" + lot.getValue().fichier(), dossier.resolve(lot.getValue().fichier()));
                ajoutes.add(lot.getKey());
            }
            if (ajoutes.isEmpty()) return;
            try (InputStream index = getResource("exemples/ressources.txt")) {
                if (index != null) {
                    for (String ligne : new String(index.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                        String f = ligne.trim();
                        if (f.isEmpty()) continue;
                        boolean voulu = premier;
                        for (String l : ajoutes) {
                            if (LOTS.containsKey(l)) for (String r : LOTS.get(l).ressources()) if (f.startsWith(r)) voulu = true;
                        }
                        if (voulu) copier("exemples/ressources/" + f, dossier.resolve("ressources").resolve(f));
                    }
                }
            }
            poses.addAll(ajoutes);
            Files.createDirectories(liste.getParent());
            Files.writeString(liste, String.join("\n", poses) + "\n");
            if (!premier) getLogger().info("Nouveaux exemples installés : " + String.join(", ", ajoutes));
        } catch (IOException e) {
            getLogger().warning("Impossible de créer les fichiers de départ : " + e.getMessage());
        }
    }

    /** Les types livrés avec le plugin : ceux qui manquent au types.yml du serveur (ajoutés depuis) sont pris d'ici. */
    private org.bukkit.configuration.file.YamlConfiguration typesParDefaut() {
        try (InputStream in = getResource("types.yml")) {
            if (in == null) return null;
            return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    static String empreinte(byte[] octets) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(octets));
        } catch (java.security.NoSuchAlgorithmException e) {
            return "";
        }
    }

    private void copier(String ressource, Path cible) throws IOException {
        if (Files.exists(cible)) return;
        try (InputStream in = getResource(ressource)) {
            if (in == null) return;
            Files.createDirectories(cible.getParent());
            Files.copy(in, cible);
        }
    }

    // ------------------------------------------------------------------ joueurs

    /** Refait l'affichage des objets de vorace-item de ce joueur (nom, description, apparence). */
    public int rafraichir(Player p) {
        int n = 0;
        ItemStack[] contenu = p.getInventory().getContents();
        for (int i = 0; i < contenu.length; i++) {
            ItemStack objet = contenu[i];
            if (objet == null || exemplaires.modele(objet) == null) continue;
            ItemStack avant = objet.clone();
            if (exemplaires.rafraichir(objet) && !objet.equals(avant)) {
                p.getInventory().setItem(i, objet);
                n++;
            }
        }
        equipementChange(p);
        return n;
    }

    /** L'équipement du joueur a peut-être changé : son bilan est recalculé au prochain coup. */
    public void equipementChange(Player p) {
        equipement.oublier(p);
    }

    /**
     * Le joueur part. Ses modificateurs d'attributs (vie max de l'équipement...) restent sur lui : ils sont enregistrés
     * avec sa sauvegarde, et le jeu borne la vie au maximum en la relisant. Les retirer ici lui faisait perdre la vie
     * gagnée grâce à son équipement à chaque reconnexion. Ils sont remis à jour toutes les demi-secondes.
     */
    void depart(Player p) {
        equipement.oublier(p);
    }
}

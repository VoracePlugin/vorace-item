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
        catalogue = Catalogue.charger(dossier, problemes);
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
            if (!Files.isRegularFile(liste) && getConfig().getBoolean("exemples", true)) {
                for (String f : EXEMPLES) copier("exemples/" + f, dossier.resolve(f));
                try (InputStream index = getResource("exemples/ressources.txt")) {
                    if (index != null) {
                        for (String ligne : new String(index.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                            String f = ligne.trim();
                            if (!f.isEmpty()) copier("exemples/ressources/" + f, dossier.resolve("ressources").resolve(f));
                        }
                    }
                }
                Files.createDirectories(liste.getParent());
                Files.writeString(liste, "exemples\n");
            }
        } catch (IOException e) {
            getLogger().warning("Impossible de créer les fichiers de départ : " + e.getMessage());
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

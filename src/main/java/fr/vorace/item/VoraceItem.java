package fr.vorace.item;

import fr.vorace.Vorace;
import fr.vorace.item.api.ProfilJoueur;
import fr.vorace.item.api.RessourcesJoueur;
import fr.vorace.item.api.VoraceItemAPI;
import fr.vorace.item.butin.Butins;
import fr.vorace.item.combat.Combat;
import fr.vorace.item.combat.Equipement;
import fr.vorace.item.combat.ProfilParDefaut;
import fr.vorace.item.combat.Ressources;
import fr.vorace.item.combat.Usure;
import fr.vorace.item.competences.Competences;
import fr.vorace.item.definitions.Catalogue;
import fr.vorace.item.exemplaire.Exemplaires;
import fr.vorace.item.exemplaire.Qualite;
import fr.vorace.item.forge.Atelier;
import fr.vorace.item.forge.Forge;
import fr.vorace.item.forge.Ingredients;
import fr.vorace.item.forge.MenuForge;
import fr.vorace.item.interfaces.EcouteurMenus;
import fr.vorace.item.pack.GenerateurPack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
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
 * vorace-item : objets RPG (stats, rareté, armes modulaires, forge, compétences d'armes, butin), appuyés sur
 * vorace-core pour le pack de ressources et l'apparence des objets.
 */
public final class VoraceItem extends JavaPlugin implements Listener {

    /** Fichiers créés au premier lancement (ils ne sont jamais remplacés ensuite). */
    private static final List<String> FICHIERS = List.of("raretes.yml", "modificateurs.yml", "types.yml", "competences.yml", "butins.yml");

    /** Exemples installés une seule fois : ressource du .jar → fichier dans plugins/VoraceItem. */
    private static final List<String> EXEMPLES = List.of("objets/exemples.yml", "materiaux/exemples.yml");

    private Vorace core;
    private Reglages reglages;
    private volatile Catalogue catalogue;
    private Qualite qualite;
    private Exemplaires exemplaires;
    private fr.vorace.item.exemplaire.Pieces pieces;
    private Competences competences;
    private Butins butins;
    private Equipement equipement;
    private Ressources ressourcesInternes;
    private ProfilParDefaut profilParDefaut;
    private Combat combat;
    private Usure usure;
    private Forge forge;
    private Atelier atelier;
    private Ingredients ingredients;

    /** Le plugin vorace-core (objets, pack, modèles). */
    public Vorace core() { return core; }
    public Reglages reglages() { return reglages; }
    public Catalogue catalogue() { return catalogue; }
    public Qualite qualite() { return qualite; }
    public Exemplaires exemplaires() { return exemplaires; }
    public fr.vorace.item.exemplaire.Pieces pieces() { return pieces; }
    public Competences competences() { return competences; }
    public Butins butins() { return butins; }
    public Equipement equipement() { return equipement; }
    public Combat combat() { return combat; }
    public Usure usure() { return usure; }
    public Forge forge() { return forge; }
    public Atelier atelier() { return atelier; }
    public Ingredients ingredients() { return ingredients; }
    public ProfilParDefaut profilParDefaut() { return profilParDefaut; }

    /** Stats des joueurs : celles du plugin des classes s'il en fournit, sinon le profil par défaut. */
    public ProfilJoueur profil() {
        ProfilJoueur p = Bukkit.getServicesManager().load(ProfilJoueur.class);
        return p == null ? profilParDefaut : p;
    }

    /** Mana et stamina : ceux du plugin des classes s'il en fournit, sinon les jauges de vorace-item. */
    public RessourcesJoueur ressources() {
        RessourcesJoueur r = Bukkit.getServicesManager().load(RessourcesJoueur.class);
        return r == null ? ressourcesInternes : r;
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
        competences = new Competences(this);
        butins = new Butins(this);
        equipement = new Equipement(this);
        ressourcesInternes = new Ressources(this);
        profilParDefaut = new ProfilParDefaut(this);
        combat = new Combat(this);
        usure = new Usure(this);
        forge = new Forge(this);
        atelier = new Atelier(this);
        ingredients = new Ingredients(this);

        installerFichiers();
        charger();

        Bukkit.getPluginManager().registerEvents(new EcouteurObjets(this), this);
        Bukkit.getPluginManager().registerEvents(new EcouteurMenus(this), this);
        Bukkit.getPluginManager().registerEvents(combat, this);
        Bukkit.getPluginManager().registerEvents(competences, this);
        Bukkit.getPluginManager().registerEvents(butins, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, ressourcesInternes::seconde, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> { for (Player p : Bukkit.getOnlinePlayers()) equipement.appliquerAttributs(p); }, 10L, 10L);

        Commande commande = new Commande(this);
        var c = getCommand("vitem");
        if (c != null) { c.setExecutor(commande); c.setTabCompleter(commande); }
        VoraceItemAPI.initialiser(this);
        getLogger().info("vorace-item " + getPluginMeta().getVersion() + " : " + catalogue.modeles().size() + " modèle(s), "
            + catalogue.pieces().size() + " pièce(s) dans " + catalogue.materiaux().size() + " matériau(x), "
            + competences.toutes().size() + " compétence(s), " + butins.tables().size() + " table(s) de butin.");
    }

    @Override
    public void onDisable() {
        if (equipement == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) depart(p);
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
        competences.charger(dossier.resolve("competences.yml"), problemes);
        butins.charger(dossier.resolve("butins.yml"), problemes);
        for (var m : catalogue.modeles()) {
            for (String id : m.competences().values()) {
                if (competences.parId(id) == null) problemes.add(m.id() + " : compétence inconnue « " + id + " » (voir competences.yml)");
            }
        }
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

    void depart(Player p) {
        equipement.retirerAttributs(p);
        equipement.oublier(p);
        ressourcesInternes.enregistrer(p);
    }

    /** Blocs qui ouvrent la forge ou l'atelier d'un clic droit (config.yml : forge.blocs, atelier.blocs). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void station(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        if (e.getPlayer().isSneaking()) return;
        String bloc = e.getClickedBlock().getType().name();
        if (reglages.forgeBlocs.contains(bloc)) {
            e.setCancelled(true);
            MenuForge.ouvrir(this, e.getPlayer(), 0);
        } else if (reglages.atelierBlocs.contains(bloc)) {
            e.setCancelled(true);
            atelier.ouvrir(e.getPlayer());
        }
    }
}

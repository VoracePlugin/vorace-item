package fr.vorace.item;

import fr.vorace.item.butin.Butins;
import fr.vorace.item.combat.ProfilParDefaut;
import fr.vorace.item.combat.Usure;
import fr.vorace.item.competences.Competences;
import fr.vorace.item.definitions.Catalogue;
import fr.vorace.item.definitions.Materiau.Piece;
import fr.vorace.item.definitions.ModeleObjet;
import fr.vorace.item.definitions.Rarete;
import fr.vorace.item.exemplaire.Calcul;
import fr.vorace.item.exemplaire.Exemplaire;
import fr.vorace.item.exemplaire.Exemplaires.Signature;
import fr.vorace.item.forge.MenuForge;
import fr.vorace.item.interfaces.EcouteurMenus;
import fr.vorace.item.interfaces.Menu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** La commande /vitem. */
public final class Commande implements CommandExecutor, TabCompleter {

    private static final List<String> SOUS = List.of("donner", "assembler", "piece", "monter", "forge", "atelier", "catalogue", "info", "reparer",
        "nommer", "profil", "butin", "competence", "pack", "recharger", "essai");

    private final VoraceItem plugin;
    private final Random hasard = new Random();

    public Commande(VoraceItem plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sous = a.length == 0 ? "aide" : a[0].toLowerCase(Locale.ROOT);
        boolean admin = s.hasPermission("voraceitem.admin");
        switch (sous) {
            case "forge" -> {
                Player p = cible(s, a, 1, admin);
                if (p == null) return true;
                if (!s.hasPermission("voraceitem.forge")) return refus(s);
                MenuForge.ouvrir(plugin, p, 0);
            }
            case "atelier" -> {
                Player p = cible(s, a, 1, admin);
                if (p == null) return true;
                if (!s.hasPermission("voraceitem.atelier")) return refus(s);
                plugin.atelier().ouvrir(p);
            }
            case "donner", "give" -> { if (!admin) return refus(s); donner(s, a); }
            case "assembler" -> { if (!admin) return refus(s); assembler(s, a); }
            case "piece" -> { if (!admin) return refus(s); piece(s, a); }
            case "monter" -> { if (!admin) return refus(s); monter(s, a); }
            case "catalogue" -> {
                if (!admin) return refus(s);
                if (!(s instanceof Player p)) { dire(s, "<red>Cette commande s'utilise en jeu."); return true; }
                catalogue(p, a.length > 1 ? entier(a[1], 1) - 1 : 0);
            }
            case "info" -> { if (!admin) return refus(s); info(s); }
            case "reparer" -> { if (!admin) return refus(s); reparer(s); }
            case "nommer" -> { if (!admin) return refus(s); nommer(s, a); }
            case "profil" -> { if (!admin) return refus(s); profil(s, a); }
            case "butin" -> { if (!admin) return refus(s); butin(s, a); }
            case "competence" -> { if (!admin) return refus(s); competence(s, a); }
            case "pack" -> {
                if (!admin) return refus(s);
                List<String> problemes = new ArrayList<>();
                boolean change = plugin.genererPack(problemes);
                problemes.forEach(m -> dire(s, "<yellow>" + Textes.echapper(m)));
                dire(s, change ? "<green>La partie du pack de vorace-item a changé : vorace-core reconstruit le pack." : "<gray>Rien n'a changé dans la partie du pack de vorace-item.");
            }
            case "recharger", "reload" -> {
                if (!admin) return refus(s);
                List<String> problemes = plugin.charger();
                for (String m : problemes) dire(s, "<yellow>" + Textes.echapper(m));
                Catalogue c = plugin.catalogue();
                dire(s, "<green>Rechargé : " + c.modeles().size() + " modèle(s), " + c.pieces().size() + " pièce(s), "
                    + plugin.competences().toutes().size() + " compétence(s), " + plugin.butins().tables().size() + " table(s) de butin"
                    + (problemes.isEmpty() ? "." : ", " + problemes.size() + " remarque(s)."));
            }
            case "essai" -> { if (!admin) return refus(s); new Essai(plugin).lancer(s); }
            default -> aide(s, admin);
        }
        return true;
    }

    private void aide(CommandSender s, boolean admin) {
        dire(s, "<white>vorace-item " + plugin.getPluginMeta().getVersion());
        s.sendMessage(Textes.objet("<#f6b73c>/vitem forge <#d9c9a8>: fabriquer une arme ou un objet"));
        s.sendMessage(Textes.objet("<#f6b73c>/vitem atelier <#d9c9a8>: réparer, améliorer, sertir une gemme"));
        if (!admin) return;
        for (String[] l : new String[][]{
            {"donner <joueur> <modèle> [rang] [place 0-100] [quantité]", "donner un objet"},
            {"assembler <joueur> <kit> <hasard|pièce,pièce,...> [rang] [place 0-100]", "donner une arme modulaire"},
            {"piece <joueur> <pièce> [quantité]", "donner une pièce d'arme seule (une lame, une garde...)"},
            {"monter <joueur> [kit] [score 0-100]", "assembler l'arme avec les pièces de son inventaire"},
            {"catalogue [page]", "tous les modèles, un clic pour en prendre un"},
            {"info", "données de l'objet tenu en main"},
            {"reparer", "réparer l'objet tenu en main"},
            {"nommer <nom>", "donner un nom à l'objet tenu en main (vide : nom du modèle)"},
            {"profil <joueur> [stat valeur | classes a,b | raz]", "stats d'essai d'un joueur"},
            {"butin <table> [joueur]", "tirer le butin d'une table"},
            {"competence <compétence> [joueur]", "lancer une compétence d'arme"},
            {"pack", "refaire la partie du pack de vorace-item"},
            {"recharger", "relire tous les fichiers"},
            {"essai", "essai automatique (sans joueur)"}}) {
            s.sendMessage(Textes.objet("<#f6b73c>/vitem " + Textes.echapper(l[0]) + " <#d9c9a8>: " + l[1]));
        }
    }

    // ------------------------------------------------------------------ objets

    private void donner(CommandSender s, String[] a) {
        if (a.length < 3) { dire(s, "<red>/vitem donner <joueur> <modèle> [rang] [place 0-100] [quantité]"); return; }
        Player p = Bukkit.getPlayerExact(a[1]);
        if (p == null) { dire(s, "<red>Joueur introuvable : " + Textes.echapper(a[1])); return; }
        Catalogue c = plugin.catalogue();
        ModeleObjet m = c.modele(a[2]);
        if (m == null) { dire(s, "<red>Modèle inconnu : " + Textes.echapper(a[2])); return; }
        if (m.kit() != null) { dire(s, "<red>« " + m.id() + " » est une arme modulaire : utilise /vitem assembler."); return; }
        Rarete rang = a.length > 3 ? c.raretes().parId(a[3]) : m.rang();
        if (rang == null) { dire(s, "<red>Rang inconnu : " + Textes.echapper(a[3]) + " " + rangs()); return; }
        if (Math.abs(rang.ordre() - m.rang().ordre()) > 1) dire(s, "<yellow>Attention : ce modèle est " + m.rang().nom() + ", il ne devrait exister qu'à un rang d'écart.");
        double position = a.length > 4 ? Math.max(0, Math.min(100, nombre(a[4], 50))) / 100.0 : 0.5;
        ItemStack objet = plugin.exemplaires().creer(m, rang, false, position, null, Signature.AUCUNE, a.length > 5 ? entier(a[5], 1) : 1);
        EcouteurMenus.rendre(p, objet);
        dire(s, "<green>Donné à " + p.getName() + " : " + m.nom() + " (" + rang.nom() + ").");
    }

    private void assembler(CommandSender s, String[] a) {
        if (a.length < 4) { dire(s, "<red>/vitem assembler <joueur> <kit> <hasard|pièce,pièce,...> [rang] [place 0-100]"); return; }
        Player p = Bukkit.getPlayerExact(a[1]);
        if (p == null) { dire(s, "<red>Joueur introuvable : " + Textes.echapper(a[1])); return; }
        Catalogue c = plugin.catalogue();
        ModeleObjet kit = c.modele(a[2]);
        if (kit == null || kit.kit() == null) { dire(s, "<red>« " + Textes.echapper(a[2]) + " » n'est pas une arme modulaire."); return; }
        Map<String, String> pieces = pieces(kit, a[3]);
        if (pieces == null) { dire(s, "<red>Pièces invalides. Il en faut une par emplacement " + kit.kit().emplacements() + ", par exemple fer/lame_droite."); return; }
        Rarete rang = a.length > 4 ? c.raretes().parId(a[4]) : c.raretes().premier();
        if (rang == null) { dire(s, "<red>Rang inconnu : " + Textes.echapper(a[4]) + " " + rangs()); return; }
        double position = a.length > 5 ? Math.max(0, Math.min(100, nombre(a[5], 50))) / 100.0 : 0.5;
        ItemStack objet = plugin.exemplaires().creer(kit, rang, false, position, pieces, Signature.AUCUNE, 1);
        if (objet == null) { dire(s, "<red>Ces pièces ne vont pas ensemble."); return; }
        EcouteurMenus.rendre(p, objet);
        dire(s, "<green>Donné à " + p.getName() + " : " + String.join(", ", pieces.values()) + " (" + rang.nom() + ").");
    }

    private void piece(CommandSender s, String[] a) {
        if (a.length < 3) { dire(s, "<red>/vitem piece <joueur> <pièce> [quantité]"); return; }
        Player p = Bukkit.getPlayerExact(a[1]);
        if (p == null) { dire(s, "<red>Joueur introuvable : " + Textes.echapper(a[1])); return; }
        Piece piece = plugin.catalogue().piece(a[2]);
        if (piece == null) { dire(s, "<red>Pièce inconnue : " + Textes.echapper(a[2]) + " (par exemple fer/lame_droite)."); return; }
        ItemStack objet = plugin.pieces().creer(piece);
        objet.setAmount(Math.max(1, Math.min(objet.getMaxStackSize(), a.length > 3 ? entier(a[3], 1) : 1)));
        EcouteurMenus.rendre(p, objet);
        dire(s, "<green>Pièce donnée à " + p.getName() + " : " + piece.id() + ".");
    }

    /** Assemble une arme avec les pièces que le joueur a dans son inventaire : la première trouvée pour chaque emplacement. */
    private void monter(CommandSender s, String[] a) {
        if (a.length < 2) { dire(s, "<red>/vitem monter <joueur> [kit] [score 0-100]"); return; }
        Player p = Bukkit.getPlayerExact(a[1]);
        if (p == null) { dire(s, "<red>Joueur introuvable : " + Textes.echapper(a[1])); return; }
        Catalogue c = plugin.catalogue();
        ModeleObjet kit = c.modele(a.length > 2 ? a[2] : "epee");
        if (kit == null || kit.kit() == null) { dire(s, "<red>« " + Textes.echapper(a.length > 2 ? a[2] : "epee") + " » n'est pas une arme modulaire."); return; }
        double score = a.length > 3 ? Math.max(0, Math.min(100, nombre(a[3], 50))) : 50;
        Map<String, Integer> cases = new LinkedHashMap<>();
        ItemStack[] contenu = p.getInventory().getStorageContents();
        for (int i = 0; i < contenu.length; i++) {
            Piece piece = c.piece(plugin.pieces().lire(contenu[i]));
            if (piece != null && piece.kit().equals(kit.id())) cases.putIfAbsent(piece.emplacement(), i);
        }
        List<ItemStack> objets = new ArrayList<>();
        for (int i : cases.values()) objets.add(contenu[i]);
        fr.vorace.item.exemplaire.Pieces.Assemblage r = plugin.pieces().assembler(kit, objets, score, null, 0, 0, p.getName(), p.getUniqueId(), hasard);
        if (r.objet() == null) { dire(s, "<red>" + Textes.echapper(r.erreur())); return; }
        for (int i : cases.values()) {
            if (contenu[i].getAmount() > 1) contenu[i].setAmount(contenu[i].getAmount() - 1);
            else contenu[i] = null;
            p.getInventory().setItem(i, contenu[i]);
        }
        EcouteurMenus.rendre(p, r.objet());
        dire(s, "<green>Monté pour " + p.getName() + " : " + String.join(", ", plugin.exemplaires().lire(r.objet()).pieces.values())
            + " (qualité " + r.qualite().grade().nom() + ", score " + Textes.nombre(r.qualite().score()) + ").");
    }

    /** « hasard », « hasard:fer+cuivre » ou une liste de pièces dans l'ordre des emplacements. Null si invalide. */
    Map<String, String> pieces(ModeleObjet kit, String texte) {
        Catalogue c = plugin.catalogue();
        Map<String, String> pieces = new LinkedHashMap<>();
        String t = texte.toLowerCase(Locale.ROOT);
        if (t.startsWith("hasard")) {
            List<String> materiaux = t.contains(":") ? List.of(t.substring(t.indexOf(':') + 1).split("\\+")) : List.of();
            for (String e : kit.kit().emplacements()) {
                List<Piece> possibles = new ArrayList<>();
                for (Piece p : c.pieces(kit.id(), e)) if (materiaux.isEmpty() || materiaux.contains(p.materiau())) possibles.add(p);
                if (possibles.isEmpty()) return null;
                pieces.put(e, possibles.get(hasard.nextInt(possibles.size())).id());
            }
            return pieces;
        }
        for (String id : t.split(",")) {
            Piece p = c.piece(id.trim());
            if (p == null || !p.kit().equals(kit.id())) return null;
            pieces.put(p.emplacement(), p.id());
        }
        for (String e : kit.kit().emplacements()) if (!pieces.containsKey(e)) return null;
        return pieces;
    }

    private void catalogue(Player p, int page) {
        List<ModeleObjet> liste = new ArrayList<>(plugin.catalogue().modeles());
        int pages = Math.max(1, (liste.size() + 44) / 45);
        int pg = Math.max(0, Math.min(pages - 1, page));
        Menu menu = new Menu("Catalogue (" + (pg + 1) + "/" + pages + ")", 6);
        for (int i = 0; i < 45 && pg * 45 + i < liste.size(); i++) {
            ModeleObjet m = liste.get(pg * 45 + i);
            Map<String, String> pieces = m.kit() == null ? null : pieces(m, "hasard");
            Rarete rang = m.kit() == null ? m.rang() : plugin.catalogue().raretes().premier();
            ItemStack apercu = m.kit() != null && pieces == null ? null
                : m.empilable() ? plugin.exemplaires().creer(m, rang, false, 0.5, null, Signature.AUCUNE, 1) : plugin.exemplaires().apercu(m, rang, pieces);
            if (apercu == null) {
                menu.bouton(i, Menu.icone("BARRIER", "<red>" + m.nom(), "Pièces manquantes."), null);
                continue;
            }
            menu.bouton(i, Menu.icone(apercu, null, true, "", Menu.DISCRET + m.id() + " · " + m.fichier(), Menu.clic("Clic", "prendre cet objet")),
                (j, d, s) -> EcouteurMenus.rendre(j, plugin.exemplaires().creer(m, rang, false, 0.5, pieces, Signature.AUCUNE, 1)));
        }
        if (pg > 0) menu.bouton(45, Menu.icone("ARROW", "<white>◀ Page précédente"), (j, d, s) -> catalogue(j, pg - 1));
        if (pg < pages - 1) menu.bouton(53, Menu.icone("ARROW", "<white>Page suivante ▶"), (j, d, s) -> catalogue(j, pg + 1));
        menu.bouton(49, Menu.icone("BARRIER", Menu.NON + "Fermer"), (j, d, s) -> j.closeInventory());
        menu.ouvrir(p);
    }

    private void info(CommandSender s) {
        if (!(s instanceof Player p)) { dire(s, "<red>Cette commande s'utilise en jeu."); return; }
        ItemStack objet = p.getInventory().getItemInMainHand();
        Exemplaire ex = plugin.exemplaires().lire(objet);
        if (ex == null) { dire(s, "<gray>L'objet en main n'est pas un objet de vorace-item."); return; }
        Catalogue c = plugin.catalogue();
        ModeleObjet m = c.modele(ex.modele);
        dire(s, "<white>" + ex.modele + (m == null ? " <red>(modèle supprimé)" : "") + " <gray>· rang <white>" + ex.rang + (ex.rebut ? " (rebut)" : "")
            + " <gray>· place <white>" + Textes.nombre(ex.position * 100) + " %");
        if (ex.id != null) s.sendMessage(Textes.objet("<gray>Identifiant : <white>" + ex.id));
        if (ex.grade != null) s.sendMessage(Textes.objet("<gray>Qualité : <white>" + ex.grade + " <gray>(score " + Textes.nombre(ex.score) + ")"));
        if (ex.fabricant != null) s.sendMessage(Textes.objet("<gray>Fabricant : <white>" + Textes.echapper(ex.fabricant)));
        if (!ex.pieces.isEmpty()) s.sendMessage(Textes.objet("<gray>Pièces : <white>" + String.join(", ", ex.pieces.values())));
        if (ex.aDurabilite()) s.sendMessage(Textes.objet("<gray>Durabilité : <white>" + ex.durabilite + " / " + ex.durabiliteMax + " <gray>(origine " + ex.durabiliteOrigine
            + ", usure de combat " + ex.usureCombat + ", " + ex.reparations + " réparation(s))"));
        if (m == null) return;
        Rarete rang = c.raretes().parId(ex.rang);
        for (Map.Entry<String, Double> e : plugin.exemplaires().stats(ex).entrySet()) {
            double[] f = rang == null ? null : Calcul.fourchette(c, m, ex, e.getKey(), rang, ex.rebut);
            s.sendMessage(Textes.objet("<gray>" + c.modificateurs().nom(e.getKey()) + " : <white>" + Textes.nombre(e.getValue())
                + (f == null ? "" : " <dark_gray>(fourchette " + Textes.nombre(f[0]) + " à " + Textes.nombre(f[1]) + ")")));
        }
    }

    private void reparer(CommandSender s) {
        if (!(s instanceof Player p)) { dire(s, "<red>Cette commande s'utilise en jeu."); return; }
        ItemStack objet = p.getInventory().getItemInMainHand();
        Usure.Reparation r = plugin.usure().reparer(p, objet, 1);
        if (r == Usure.Reparation.FAIT) p.getInventory().setItemInMainHand(objet);
        dire(s, switch (r) {
            case FAIT -> "<green>Objet réparé.";
            case PAS_UN_OBJET -> "<gray>L'objet en main n'est pas un objet de vorace-item.";
            case SANS_DURABILITE -> "<gray>Cet objet ne s'use pas.";
            case INTACT -> "<gray>Cet objet n'est pas abîmé.";
            case TROP_USE -> "<red>Cet objet est trop usé : il ne se répare plus.";
        });
    }

    private void nommer(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { dire(s, "<red>Cette commande s'utilise en jeu."); return; }
        ItemStack objet = p.getInventory().getItemInMainHand();
        Exemplaire ex = plugin.exemplaires().lire(objet);
        if (ex == null || ex.id == null) { dire(s, "<gray>L'objet en main n'est pas une arme ou une armure de vorace-item."); return; }
        ex.nom = a.length < 2 ? null : String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length));
        plugin.exemplaires().ecrire(objet, ex);
        p.getInventory().setItemInMainHand(objet);
        dire(s, ex.nom == null ? "<green>L'objet reprend le nom de son modèle." : "<green>Objet nommé « " + Textes.echapper(ex.nom) + " ».");
    }

    // ------------------------------------------------------------------ profil, butin, compétences

    private void profil(CommandSender s, String[] a) {
        if (a.length < 2) { dire(s, "<red>/vitem profil <joueur> [stat valeur | classes a,b | raz]"); return; }
        Player p = Bukkit.getPlayerExact(a[1]);
        if (p == null) { dire(s, "<red>Joueur introuvable : " + Textes.echapper(a[1])); return; }
        ProfilParDefaut profil = plugin.profilParDefaut();
        if (plugin.profil() != profil) dire(s, "<yellow>Un autre plugin fournit les stats des joueurs : ces valeurs d'essai ne servent pas.");
        if (a.length >= 3 && a[2].equalsIgnoreCase("raz")) {
            for (String st : ProfilParDefaut.STATS) profil.fixer(p, st, null);
            profil.fixerClasses(p, null);
            dire(s, "<green>Profil de " + p.getName() + " remis aux valeurs de config.yml.");
        } else if (a.length >= 3 && a[2].equalsIgnoreCase("classes")) {
            profil.fixerClasses(p, a.length > 3 ? a[3] : null);
            dire(s, "<green>Classes de " + p.getName() + " : " + (a.length > 3 ? Textes.echapper(a[3]) : "aucune") + ".");
        } else if (a.length >= 4) {
            String st = normaliser(a[2]);
            if (!ProfilParDefaut.STATS.contains(st)) { dire(s, "<red>Stat inconnue. Stats : " + String.join(", ", ProfilParDefaut.STATS)); return; }
            profil.fixer(p, st, nombre(a[3], 0));
            dire(s, "<green>" + p.getName() + " : " + st + " = " + Textes.nombre(nombre(a[3], 0)) + ".");
        }
        plugin.equipementChange(p);
        StringBuilder sb = new StringBuilder("<gray>" + p.getName() + " :");
        for (String st : ProfilParDefaut.STATS) {
            double v = st.equals("niveau") ? profil.niveau(p) : profil.valeur(p, st);
            sb.append(" <gray>").append(st).append(" <white>").append(Textes.nombre(v));
        }
        if (!profil.classes(p).isEmpty()) sb.append(" <gray>classes <white>").append(Textes.echapper(profil.classes(p)));
        dire(s, sb.toString());
    }

    private static String normaliser(String stat) {
        String c = Textes.cle(stat).replace('_', '-');
        return switch (c) {
            case "for" -> "force";
            case "int" -> "intelligence";
            case "con" -> "constitution";
            case "agi" -> "agilite";
            default -> c;
        };
    }

    private void butin(CommandSender s, String[] a) {
        if (a.length < 2) { dire(s, "<red>/vitem butin <table> [joueur]. Tables : " + String.join(", ", plugin.butins().tables().keySet())); return; }
        Butins.Table t = plugin.butins().tables().get(a[1].toLowerCase(Locale.ROOT));
        if (t == null) { dire(s, "<red>Table inconnue. Tables : " + String.join(", ", plugin.butins().tables().keySet())); return; }
        Player p = cible(s, a, 2, true);
        if (p == null) return;
        List<ItemStack> objets = plugin.butins().tirer(t, p, 0, true);
        if (objets.isEmpty()) { dire(s, "<yellow>La table n'a rien donné (entrée impossible à créer)."); return; }
        for (ItemStack o : objets) EcouteurMenus.rendre(p, o);
        dire(s, "<green>Butin de « " + t.id() + " » donné à " + p.getName() + ".");
    }

    private void competence(CommandSender s, String[] a) {
        if (a.length < 2) { dire(s, "<red>/vitem competence <compétence> [joueur]. Compétences : " + String.join(", ", plugin.competences().toutes().keySet())); return; }
        Player p = cible(s, a, 2, true);
        if (p == null) return;
        Competences.Lancer r = plugin.competences().lancer(p, a[1], null, false);
        dire(s, r == Competences.Lancer.FAIT ? "<green>Compétence lancée." : "<red>Compétence inconnue. Compétences : " + String.join(", ", plugin.competences().toutes().keySet()));
    }

    // ------------------------------------------------------------------ outils

    private Player cible(CommandSender s, String[] a, int index, boolean admin) {
        if (a.length > index && admin) {
            Player p = Bukkit.getPlayerExact(a[index]);
            if (p == null) dire(s, "<red>Joueur introuvable : " + Textes.echapper(a[index]));
            return p;
        }
        if (s instanceof Player p) return p;
        dire(s, "<red>Précise un joueur.");
        return null;
    }

    private String rangs() {
        List<String> l = new ArrayList<>();
        for (Rarete r : plugin.catalogue().raretes().tous()) l.add(r.id());
        return l.toString();
    }

    private static int entier(String s, int defaut) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return defaut; }
    }

    private static double nombre(String s, double defaut) {
        try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (NumberFormatException e) { return defaut; }
    }

    private static void dire(CommandSender s, String mm) {
        s.sendMessage(Textes.message(mm));
    }

    private static boolean refus(CommandSender s) {
        dire(s, "<red>Tu n'as pas la permission.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        boolean admin = s.hasPermission("voraceitem.admin");
        List<String> choix = new ArrayList<>();
        if (a.length == 1) {
            choix.addAll(admin ? SOUS : List.of("forge", "atelier"));
        } else if (admin) {
            String sous = a[0].toLowerCase(Locale.ROOT);
            Catalogue c = plugin.catalogue();
            if (a.length == 2) {
                switch (sous) {
                    case "donner", "assembler", "piece", "monter", "profil", "forge", "atelier" -> Bukkit.getOnlinePlayers().forEach(p -> choix.add(p.getName()));
                    case "butin" -> choix.addAll(plugin.butins().tables().keySet());
                    case "competence" -> choix.addAll(plugin.competences().toutes().keySet());
                    default -> { }
                }
            } else if (a.length == 3) {
                switch (sous) {
                    case "donner" -> c.modeles().forEach(m -> { if (m.kit() == null) choix.add(m.id()); });
                    case "assembler", "monter" -> c.modeles().forEach(m -> { if (m.kit() != null) choix.add(m.id()); });
                    case "piece" -> c.modeles().forEach(m -> { if (m.kit() != null) for (String e : m.kit().emplacements()) c.pieces(m.id(), e).forEach(x -> choix.add(x.id())); });
                    case "profil" -> { choix.addAll(ProfilParDefaut.STATS); choix.add("classes"); choix.add("raz"); }
                    case "butin", "competence" -> Bukkit.getOnlinePlayers().forEach(p -> choix.add(p.getName()));
                    default -> { }
                }
            } else if (a.length == 4 && sous.equals("donner") || a.length == 5 && sous.equals("assembler")) {
                c.raretes().tous().forEach(r -> choix.add(r.id()));
            } else if (a.length == 4 && sous.equals("assembler")) {
                choix.add("hasard");
                ModeleObjet kit = c.modele(a[2]);
                String deja = a[3].contains(",") ? a[3].substring(0, a[3].lastIndexOf(',') + 1) : "";
                if (kit != null && kit.kit() != null) c.pieces().forEach(p -> { if (p.kit().equals(kit.id())) choix.add(deja + p.id()); });
            }
        }
        String debut = a[a.length - 1].toLowerCase(Locale.ROOT);
        choix.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(debut));
        return choix;
    }
}

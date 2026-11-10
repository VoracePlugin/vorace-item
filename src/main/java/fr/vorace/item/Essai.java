package fr.vorace.item;

import fr.vorace.item.butin.Butins;
import fr.vorace.item.combat.Equipement;
import fr.vorace.item.combat.Formule;
import fr.vorace.item.combat.Usure;
import fr.vorace.item.competences.Competences;
import fr.vorace.item.definitions.Catalogue;
import fr.vorace.item.definitions.ModeleObjet;
import fr.vorace.item.definitions.Modificateurs;
import fr.vorace.item.definitions.Rarete;
import fr.vorace.item.exemplaire.Calcul;
import fr.vorace.item.exemplaire.Exemplaire;
import fr.vorace.item.exemplaire.Exemplaires.Signature;
import fr.vorace.item.exemplaire.Qualite;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pig;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Essai automatique sur un vrai serveur (« /vitem essai »), avec les objets d'exemple : création et relecture d'un
 * exemplaire, arme modulaire, fourchettes de stats, qualité, réparation, gemmes, butin, pack de ressources. Si un
 * joueur est connecté (le faux joueur de l'essai automatique suffit), le combat et les compétences sont essayés sur
 * un animal posé devant lui. Chaque ligne commence par « ESSAI OK » ou « ESSAI ECHEC ».
 */
public final class Essai {

    private final VoraceItem plugin;
    private final List<String> lignes = new ArrayList<>();
    private int reussis, echecs;

    public Essai(VoraceItem plugin) {
        this.plugin = plugin;
    }

    private void verifier(boolean ok, String quoi) {
        if (ok) reussis++; else echecs++;
        lignes.add((ok ? "ESSAI OK : " : "ESSAI ECHEC : ") + quoi);
    }

    public void lancer(CommandSender s) {
        try {
            objets();
            pack();
            Player joueur = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
            if (joueur != null) combat(joueur);
            else lignes.add("ESSAI INFO : aucun joueur connecté, combat et compétences non essayés");
        } catch (Throwable t) {
            verifier(false, "erreur inattendue : " + t);
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Essai", t);
        }
        lignes.add("ESSAI VORACE-ITEM : " + reussis + " réussi(s), " + echecs + " échec(s)");
        for (String l : lignes) {
            plugin.getLogger().info(l);
            if (s instanceof Player) s.sendMessage(Textes.objet((l.contains("ECHEC") ? "<red>" : "<gray>") + Textes.echapper(l)));
        }
    }

    private void objets() {
        Catalogue c = plugin.catalogue();
        Rarete commun = c.raretes().premier();
        Rarete rare = c.raretes().parId("rare");

        // modèle simple : création, relecture après enregistrement, stats dans la fourchette
        ModeleObjet dague = c.modele("dague_de_chasse");
        verifier(dague != null, "le modèle d'exemple « dague_de_chasse » est chargé");
        if (dague != null) {
            ItemStack objet = plugin.exemplaires().creer(dague, rare, false, 0.5, null, new Signature("Essai", null, "fine", 60, null), 1);
            ItemStack relu = ItemStack.deserializeBytes(objet.serializeAsBytes());
            Exemplaire ex = plugin.exemplaires().lire(relu);
            verifier(ex != null && "dague_de_chasse".equals(ex.modele) && "rare".equals(ex.rang) && ex.id != null && "Essai".equals(ex.fabricant),
                "un exemplaire garde son modèle, son rang, son identifiant et son fabricant après enregistrement");
            double[] f = Calcul.fourchette(c, dague, ex, Modificateurs.ATTAQUE, rare, false);
            double attaque = plugin.exemplaires().stats(ex).getOrDefault(Modificateurs.ATTAQUE, 0.0);
            verifier(f != null && Math.abs(attaque - Math.round((f[0] + f[1]) / 2)) < 1e-6, "l'attaque est au milieu de la fourchette Rare, arrondie à l'unité (" + Textes.nombre(attaque) + ")");
            double[] dessous = Calcul.fourchette(c, dague, ex, Modificateurs.ATTAQUE, c.raretes().precedent(rare), false);
            verifier(f != null && dessous != null && Math.abs(dessous[1] - f[0]) < 1e-9, "les fourchettes de deux rangs voisins se touchent");
            ItemMeta meta = relu.getItemMeta();
            verifier(meta.hasDisplayName() && meta.hasLore() && meta.lore().size() >= 4, "l'objet a un nom et une description");
            verifier(ex.bonus.size() == rare.lignesBonus(), "l'objet Rare a " + rare.lignesBonus() + " ligne(s) bonus");
            verifier(meta instanceof Damageable d && d.hasMaxDamage() && d.getMaxDamage() == ex.durabiliteMax, "la barre de durabilité suit celle de l'objet (" + ex.durabiliteMax + ")");

            // usure puis réparation : le maximum baisse un peu pour de bon
            ex.durabilite = ex.durabiliteMax / 2;
            plugin.exemplaires().ecrire(relu, ex);
            int maxAvant = ex.durabiliteMax;
            Usure.Reparation r = plugin.usure().reparer(null, relu, 1);
            Exemplaire repare = plugin.exemplaires().lire(relu);
            verifier(r == Usure.Reparation.FAIT && repare.durabilite == repare.durabiliteMax && repare.durabiliteMax < maxAvant && repare.reparations == 1,
                "la réparation remet la durabilité au maximum, qui baisse de " + (maxAvant - repare.durabiliteMax));

            // gemme
            ModeleObjet rubis = c.modele("rubis_taille");
            if (rubis != null) {
                ItemStack gemme = plugin.exemplaires().creer(rubis, rubis.rang(), false, 0.5, null, Signature.AUCUNE, 2);
                double avant = plugin.exemplaires().stats(relu).getOrDefault("physical-damage", 0.0);
                boolean serti = plugin.atelier().sertir(relu, gemme);
                double apres = plugin.exemplaires().stats(relu).getOrDefault("physical-damage", 0.0);
                verifier(serti && gemme.getAmount() == 1 && apres > avant, "une gemme sertie ajoute son modificateur (" + Textes.nombre(avant) + " → " + Textes.nombre(apres) + ")");
                verifier(!plugin.atelier().sertir(relu, gemme), "pas de seconde gemme quand il n'y a plus d'emplacement libre");
            }
        }

        // arme modulaire
        ModeleObjet epee = c.modele("epee");
        verifier(epee != null && epee.kit() != null, "le modèle modulaire d'exemple « epee » est chargé");
        if (epee != null && epee.kit() != null) {
            Map<String, String> pieces = new LinkedHashMap<>();
            pieces.put("lame", "fer/lame_droite");
            pieces.put("garde", "acier/garde_croissant");
            pieces.put("poignee", "cuivre/poignee_cuir");
            pieces.put("pommeau", "fer/pommeau_rond");
            ItemStack arme = plugin.exemplaires().creer(epee, commun, false, 0, pieces, Signature.AUCUNE, 1);
            verifier(arme != null, "une épée s'assemble avec des pièces de trois matériaux");
            if (arme != null) {
                ItemMeta meta = arme.getItemMeta();
                List<String> chaines = meta.getCustomModelDataComponent().getStrings();
                verifier(meta.hasItemModel() && meta.getItemModel().toString().equals("voraceitem:kit/epee") && chaines.size() == 5
                    && chaines.get(0).equals("fer/lame_droite") && chaines.get(1).equals("acier/garde_croissant"),
                    "l'épée porte le modèle composé et une chaîne par pièce " + chaines);
                Exemplaire ex = plugin.exemplaires().lire(arme);
                double attendu = 15 + 3 * c.raretes().multiplicateurPalier(2) + 1 + 1;
                double attaque = plugin.exemplaires().stats(ex).getOrDefault(Modificateurs.ATTAQUE, 0.0);
                verifier(Math.abs(attaque - Math.round(attendu)) < 1e-6, "son attaque est la somme de ses pièces, la garde d'acier comptant son palier (" + Textes.nombre(attaque) + ")");
                String nom = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.displayName());
                verifier(nom.equals("Épée de fer"), "elle s'appelle « " + nom + " », d'après le matériau de sa lame");
            }
            Map<String, String> incomplet = new LinkedHashMap<>(pieces);
            incomplet.remove("pommeau");
            verifier(plugin.exemplaires().creer(epee, commun, false, 0, incomplet, Signature.AUCUNE, 1) == null, "une épée sans pommeau est refusée");

            // les pièces en objets : fabriquées une à une, puis assemblées
            List<ItemStack> objets = new java.util.ArrayList<>();
            double[] scores = {90, 80, 60, 50};
            int n = 0;
            for (String id : pieces.values()) objets.add(plugin.pieces().creer(c.piece(id), scores[n++]));
            fr.vorace.item.exemplaire.Pieces.PieceObjet lue = plugin.pieces().lire(objets.get(0));
            ItemMeta metaLame = objets.get(0).getItemMeta();
            verifier(lue != null && lue.id().equals("fer/lame_droite") && Math.abs(lue.score() - 90) < 1e-9
                && metaLame.hasItemModel() && metaLame.getItemModel().toString().equals("voraceitem:piece/fer/lame_droite")
                && objets.get(0).getMaxStackSize() == 1, "une lame seule est un objet : elle garde sa pièce, son score caché et son apparence");
            boolean rienEcrit = true;
            for (ItemStack o : objets) rienEcrit &= !o.getItemMeta().hasLore();
            String nomLame = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(metaLame.displayName());
            verifier(rienEcrit && nomLame.equals("Lame droite de fer"), "rien n'est écrit sur une pièce à part son nom (« " + nomLame + " ») : ni stats, ni qualité");
            verifier(plugin.pieces().lire(arme) == null && plugin.exemplaires().lire(objets.get(0)) == null, "une pièce n'est pas une arme, une arme n'est pas une pièce");
            fr.vorace.item.exemplaire.Pieces.Assemblage monte = plugin.pieces().assembler(epee, objets, null, 0, 0, "Essai", null, new java.util.Random(1));
            verifier(monte.objet() != null && Math.abs(monte.qualite().score() - 70) < 1e-9 && monte.qualite().grade().id().equals("excellente"),
                "quatre pièces de 90, 80, 60 et 50 donnent une épée de score 70 (Excellente)");
            if (monte.objet() != null) {
                Exemplaire ex = plugin.exemplaires().lire(monte.objet());
                verifier(ex != null && ex.pieces.equals(pieces) && "Essai".equals(ex.fabricant), "l'épée montée porte ses quatre pièces et le nom de celui qui l'a assemblée");
            }
            verifier(plugin.pieces().assembler(epee, objets.subList(0, 3), null, 0, 0, null, null, null).objet() == null, "sans pommeau, pas d'assemblage");
            List<ItemStack> doublon = new java.util.ArrayList<>(objets);
            doublon.add(plugin.pieces().creer(c.piece("cuivre/lame_large"), 40));
            verifier(plugin.pieces().assembler(epee, doublon, null, 0, 0, null, null, null).objet() == null, "deux lames, pas d'assemblage");
            verifier(Files.isRegularFile(plugin.core().packs().racineServeur().resolve(plugin.reglages().dossierPack).resolve("assets/voraceitem/items/piece/fer/lame_droite.json")),
                "chaque pièce a son objet dans le pack");
        }

        // qualité : un Brut de matériau Commun tombe dans la fourchette « rebut », un Chef-d'œuvre peut gagner un rang
        Qualite q = plugin.qualite();
        Qualite.Resultat brut = q.calculer(10, commun, 0, 0, new Random(1));
        verifier(brut.grade() == Qualite.Grade.BRUTE && brut.rebut() && brut.rang().equals(commun), "un objet Brut de matériau Commun est un rebut");
        Qualite.Resultat chef = q.calculer(95, rare, 1.0, 0, new Random(1));
        verifier(chef.grade() == Qualite.Grade.CHEF_D_OEUVRE && chef.rang().id().equals("epique"), "un Chef-d'œuvre Rare peut sortir Épique");

        // butin
        Butins.Table table = plugin.butins().tables().get("etage_5");
        verifier(table != null, "la table de butin d'exemple « etage_5 » est chargée");
        if (table != null) {
            int armes = 0;
            for (int i = 0; i < 40; i++) {
                for (ItemStack o : plugin.butins().tirer(table, null, 0, true)) if ("epee".equals(plugin.exemplaires().modele(o))) armes++;
            }
            verifier(armes > 0, "la table lâche des épées assemblées (" + armes + " sur 40 tirages)");
        }
    }

    private void pack() throws Exception {
        Path dossier = plugin.core().packs().racineServeur().resolve(plugin.reglages().dossierPack);
        Path kit = dossier.resolve("assets/voraceitem/items/kit/epee.json");
        verifier(Files.isRegularFile(kit) && Files.readString(kit).contains("minecraft:composite") && Files.readString(kit).contains("fer/lame_droite"),
            "la définition composée de l'épée est écrite dans " + plugin.reglages().dossierPack);
        verifier(Files.isRegularFile(dossier.resolve("assets/voraceitem/models/item/piece/fer/lame_droite.json"))
            && Files.isRegularFile(dossier.resolve("assets/voraceitem/textures/item/exemple/fer/lame_droite.png")), "le modèle et la texture d'une pièce y sont aussi");
        Path zip = plugin.core().packs().dossierGenere().resolve("pack.zip");
        boolean dedans = false;
        if (Files.isRegularFile(zip)) {
            try (ZipFile z = new ZipFile(zip.toFile())) {
                Enumeration<? extends ZipEntry> it = z.entries();
                while (it.hasMoreElements()) if (it.nextElement().getName().equals("assets/voraceitem/items/kit/epee.json")) { dedans = true; break; }
            }
        }
        verifier(dedans, "le pack construit par vorace-core contient l'épée modulaire");
    }

    private void combat(Player p) {
        Catalogue c = plugin.catalogue();
        ModeleObjet epee = c.modele("epee");
        if (epee == null || epee.kit() == null) return;
        ItemStack avantMain = p.getInventory().getItemInMainHand();
        Map<String, String> pieces = new LinkedHashMap<>();
        pieces.put("lame", "fer/lame_droite");
        pieces.put("garde", "fer/garde_droite");
        pieces.put("poignee", "fer/poignee_cuir");
        pieces.put("pommeau", "fer/pommeau_rond");
        ItemStack arme = plugin.exemplaires().creer(epee, c.raretes().premier(), false, 0, pieces, Signature.AUCUNE, 1);
        p.getInventory().setItemInMainHand(arme);
        plugin.equipementChange(p);
        Equipement.Bilan bilan = plugin.equipement().bilan(p);
        verifier(bilan.arme() != null && Math.abs(bilan.total(Modificateurs.ATTAQUE) - 19) < 1e-6, "l'épée en main compte dans l'équipement (attaque " + Textes.nombre(bilan.total(Modificateurs.ATTAQUE)) + ")");

        Location devant = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(2));
        Pig cible = p.getWorld().spawn(devant, Pig.class, z -> {
            z.setAI(false);
            z.setSilent(true);
            z.getAttribute(Attribute.MAX_HEALTH).setBaseValue(500);
            z.setHealth(500);
            z.getAttribute(Attribute.ARMOR).setBaseValue(5);
        });
        try {
            double puissance = Formule.puissance(19, epee.scales(), st -> plugin.profil().stat(p, st), 0, 1);
            double attendu = Formule.arrondir(Formule.degats(puissance, 5 * plugin.reglages().defenseParArmureVanilla));
            double infliges = plugin.combat().frapper(p, cible, 1, null, null);
            double perdus = 500 - cible.getHealth();
            boolean critique = infliges > attendu;
            verifier(infliges > 0 && Math.abs(perdus - infliges) < 0.01 && (critique || Math.abs(infliges - attendu) < 0.01),
                "un coup suit la formule : puissance " + Textes.nombre(puissance) + ", défense " + Textes.nombre(5 * plugin.reglages().defenseParArmureVanilla)
                    + ", " + Textes.nombre(infliges) + " dégât(s)" + (critique ? " (critique)" : ""));

            cible.setHealth(500);
            cible.setNoDamageTicks(0);
            Competences.Lancer l = plugin.competences().lancer(p, "entaille_tournoyante", null, false);
            verifier(l == Competences.Lancer.FAIT && cible.getHealth() < 500, "la compétence « Entaille tournoyante » touche la cible ("
                + Textes.nombre(500 - cible.getHealth()) + " dégât(s))");

            double staminaAvant = plugin.ressources().stamina(p);
            Competences.Lancer premier = plugin.competences().lancer(p, "entaille", p.getInventory().getItemInMainHand(), true);
            Competences.Lancer second = plugin.competences().lancer(p, "entaille", p.getInventory().getItemInMainHand(), true);
            verifier(premier == Competences.Lancer.FAIT && second == Competences.Lancer.RECHARGE && plugin.ressources().stamina(p) < staminaAvant,
                "une compétence coûte de la stamina puis se recharge");
            if (!epee.competences().isEmpty())                       // les objets d'exemple n'ont plus de compétence (0.10.3)
                verifier(p.getCooldown(p.getInventory().getItemInMainHand()) > 0, "le temps de recharge s'affiche sur l'arme");

            // usure : l'arme perd de la durabilité, comptée comme usure de combat
            Exemplaire avantUsure = plugin.exemplaires().lire(p.getInventory().getItemInMainHand());
            plugin.usure().user(p, EquipmentSlot.HAND, 5, true);
            Exemplaire apresUsure = plugin.exemplaires().lire(p.getInventory().getItemInMainHand());
            verifier(apresUsure.durabilite == avantUsure.durabilite - 5 && apresUsure.usureCombat == 5, "l'arme s'use (" + avantUsure.durabilite + " → " + apresUsure.durabilite + ")");

            // amélioration : la première pierre réussit toujours avec les réglages d'exemple
            ModeleObjet pierre = c.modele("pierre_d_amelioration");
            if (pierre != null && plugin.reglages().chanceAmelioration(1) >= 1) {
                ItemStack objet = p.getInventory().getItemInMainHand();
                ItemStack pierres = plugin.exemplaires().creer(pierre, pierre.rang(), false, 0.5, null, Signature.AUCUNE, 2);
                boolean change = plugin.atelier().ameliorer(p, objet, pierres);
                p.getInventory().setItemInMainHand(objet);
                plugin.equipementChange(p);
                double attaque = plugin.equipement().bilan(p).total(Modificateurs.ATTAQUE);
                verifier(change && pierres.getAmount() == 1 && Math.abs(attaque - Math.round(19 * (1 + plugin.reglages().ameliorationBonus))) < 1e-6,
                    "une pierre d'amélioration passe l'épée à +1 (attaque " + Textes.nombre(attaque) + ")");
            }

            // casque 3D : porté sur la tête sans dessin d'armure ; plastron d'exemple : son dessin, sur le torse
            ModeleObjet casque3d = c.modele("casque_acier"), cuirasse = c.modele("plastron_acier");
            if (casque3d != null && cuirasse != null) {
                org.bukkit.inventory.meta.ItemMeta mc = plugin.exemplaires().creer(casque3d, casque3d.rang(), false, 0, null, Signature.AUCUNE, 1).getItemMeta();
                org.bukkit.inventory.meta.ItemMeta mp = plugin.exemplaires().creer(cuirasse, cuirasse.rang(), false, 0, null, Signature.AUCUNE, 1).getItemMeta();
                verifier(mc.hasEquippable() && mc.getEquippable().getSlot() == org.bukkit.inventory.EquipmentSlot.HEAD && mc.getEquippable().getModel() == null,
                    "le casque 3D se porte sur la tête, sans dessin d'armure (le jeu montre son modèle)");
                verifier(mp.hasEquippable() && mp.getEquippable().getSlot() == org.bukkit.inventory.EquipmentSlot.CHEST && mp.getEquippable().getModel() != null,
                    "la cuirasse d'exemple se porte sur le torse avec son dessin");
            }
            // armure 3D du fer : le composant lu par le mod Armor Model API est posé, et gardé après l'écriture de vorace-item
            ModeleObjet fer3d = c.modele("plastron_fer");
            if (fer3d != null) {
                ItemStack it = plugin.exemplaires().creer(fer3d, fer3d.rang(), false, 0, null, Signature.AUCUNE, 1);
                String comp = it.getItemMeta().getAsComponentString();
                verifier(comp.contains("armor_model_api") && comp.contains("voraceitem:geo/exemple/fer_b.geo.json") && plugin.exemplaires().lire(it) != null,
                    "armure 3D du fer : custom_data.armor_model_api posé, données de vorace-item gardées (" + comp.length() + " car.)");
            }
            // armure : un coup reçu d'une créature est réduit par la défense du joueur
            ModeleObjet plastron = c.modele("plastron_cuir_cloute");
            org.bukkit.Difficulty difficulte = p.getWorld().getDifficulty();
            if (plastron != null && p.getGameMode() == org.bukkit.GameMode.SURVIVAL && difficulte != org.bukkit.Difficulty.PEACEFUL) {
                ItemStack avantTorse = p.getInventory().getChestplate();
                double vieAvant = p.getHealth();
                double armureAvant = plugin.equipement().bilan(p).total(Modificateurs.ARMURE);
                try {
                    p.getInventory().setChestplate(plugin.exemplaires().creer(plastron, plastron.rang(), false, 0, null, Signature.AUCUNE, 1));
                    plugin.equipementChange(p);
                    plugin.equipement().appliquerAttributs(p);
                    double vieMax = p.getAttribute(Attribute.MAX_HEALTH).getValue();
                    verifier(Math.abs(plugin.equipement().bilan(p).total(Modificateurs.ARMURE) - armureAvant - 14) < 1e-6 && Math.abs(vieMax - (p.getAttribute(Attribute.MAX_HEALTH).getBaseValue() + 6)) < 1e-6,
                        "le plastron porté ajoute son armure (+14) et sa vie max (+6)");
                    p.setHealth(Math.min(vieMax, 20));
                    p.setNoDamageTicks(0);
                    double defense = plugin.combat().defense(p, false);
                    // le jeu de base ajuste d'abord le coup d'une créature à la difficulté du monde
                    double force = difficulte == org.bukkit.Difficulty.EASY ? Math.min(10 / 2.0 + 1, 10) : difficulte == org.bukkit.Difficulty.HARD ? 15 : 10;
                    double attendu2 = Formule.arrondir(Formule.degats(force * plugin.reglages().attaqueMonstres, defense));
                    double avantCoup = p.getHealth();
                    p.damage(10, cible);
                    double recu = avantCoup - p.getHealth();
                    verifier(Math.abs(recu - attendu2) < 0.01, "un coup de " + Textes.nombre(force) + " reçu avec " + Textes.nombre(defense) + " de défense fait " + Textes.nombre(recu) + " dégât(s)");
                } finally {
                    p.getInventory().setChestplate(avantTorse);
                    plugin.equipementChange(p);
                    plugin.equipement().appliquerAttributs(p);
                    p.setHealth(Math.min(vieAvant, p.getAttribute(Attribute.MAX_HEALTH).getValue()));
                }
            }

            // conditions : une arme dont le joueur ne remplit pas les conditions ne compte pas
            ModeleObjet lame = c.modele("lame_du_veilleur");
            if (lame != null && plugin.profil() == plugin.profilParDefaut()) {
                p.getInventory().setItemInMainHand(plugin.exemplaires().creer(lame, lame.rang(), false, 0, null, Signature.AUCUNE, 1));
                plugin.profilParDefaut().fixer(p, "niveau", 1.0);
                plugin.profilParDefaut().fixerClasses(p, null);
                plugin.equipementChange(p);
                boolean refusee = plugin.equipement().bilan(p).arme() == null;
                plugin.profilParDefaut().fixer(p, "niveau", 50.0);
                plugin.profilParDefaut().fixerClasses(p, "guerrier");
                plugin.equipementChange(p);
                boolean acceptee = plugin.equipement().bilan(p).arme() != null;
                plugin.profilParDefaut().fixer(p, "niveau", null);
                plugin.profilParDefaut().fixerClasses(p, null);
                verifier(refusee && acceptee, "la Lame du Veilleur ne compte que pour un guerrier de niveau 40 ou plus");
            }
        } finally {
            cible.remove();
            p.getInventory().setItemInMainHand(avantMain);
            plugin.equipementChange(p);
        }
    }
}

package fr.vorace.item;

import fr.vorace.item.equipement.Equipement;
import fr.vorace.item.equipement.Usure;
import fr.vorace.item.definitions.Catalogue;
import fr.vorace.item.definitions.ModeleObjet;
import fr.vorace.item.definitions.Modificateurs;
import fr.vorace.item.definitions.Rarete;
import fr.vorace.item.exemplaire.Calcul;
import fr.vorace.item.exemplaire.Exemplaire;
import fr.vorace.item.exemplaire.Exemplaires.Signature;
import fr.vorace.item.exemplaire.Qualite;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
 * exemplaire, arme modulaire, fourchettes de stats, qualité, réparation, gemmes, amélioration, pack de ressources. Si
 * un joueur est connecté (le faux joueur de l'essai automatique suffit), l'équipement porté, l'usure et les conditions
 * sont essayés sur lui. Chaque ligne commence par « ESSAI OK » ou « ESSAI ECHEC ».
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
            if (joueur != null) equipement(joueur);
            else lignes.add("ESSAI INFO : aucun joueur connecté, équipement non essayé");
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
                // la gemme est posée par un autre plugin (métiers) dans les données de l'objet, puis enregistrée
                double avant = plugin.exemplaires().stats(relu).getOrDefault("physical-damage", 0.0);
                Exemplaire avecGemme = plugin.exemplaires().lire(relu);
                avecGemme.gemmes.add(rubis.id());
                boolean enregistre = fr.vorace.item.api.VoraceItemAPI.enregistrer(relu, avecGemme);
                double apres = plugin.exemplaires().stats(relu).getOrDefault("physical-damage", 0.0);
                verifier(gemme != null && enregistre && apres > avant, "une gemme posée dans l'objet ajoute son modificateur (" + Textes.nombre(avant) + " → " + Textes.nombre(apres) + ")");
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

    private void equipement(Player p) {
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
        try {
            // usure : l'arme perd de la durabilité, comptée comme usure de combat
            Exemplaire avantUsure = plugin.exemplaires().lire(p.getInventory().getItemInMainHand());
            plugin.usure().user(p, EquipmentSlot.HAND, 5, true);
            Exemplaire apresUsure = plugin.exemplaires().lire(p.getInventory().getItemInMainHand());
            verifier(apresUsure.durabilite == avantUsure.durabilite - 5 && apresUsure.usureCombat == 5, "l'arme s'use (" + avantUsure.durabilite + " → " + apresUsure.durabilite + ")");

            // le jeu veut user l'arme (un coup porté) : le jeu n'y touche pas, l'objet perd sa durabilité de vorace-item
            ItemStack enMain = p.getInventory().getItemInMainHand();
            int avantJeu = plugin.exemplaires().lire(enMain).durabilite;
            org.bukkit.event.player.PlayerItemDamageEvent usureDuJeu = new org.bukkit.event.player.PlayerItemDamageEvent(p, enMain, 3);
            Bukkit.getPluginManager().callEvent(usureDuJeu);
            int apresJeu = plugin.exemplaires().lire(p.getInventory().getItemInMainHand()).durabilite;
            verifier(usureDuJeu.isCancelled() && apresJeu == avantJeu - plugin.reglages().usureParUtilisation,
                "l'usure du jeu est remplacée par celle de vorace-item (" + avantJeu + " → " + apresJeu + ")");

            // amélioration : le niveau est posé par un autre plugin dans les données de l'objet
            ItemStack objet = p.getInventory().getItemInMainHand();
            Exemplaire ameliore = plugin.exemplaires().lire(objet);
            ameliore.amelioration = 1;
            fr.vorace.item.api.VoraceItemAPI.enregistrer(objet, ameliore);
            p.getInventory().setItemInMainHand(objet);
            plugin.equipementChange(p);
            double attaque = plugin.equipement().bilan(p).total(Modificateurs.ATTAQUE);
            verifier(Math.abs(attaque - Math.round(19 * (1 + plugin.reglages().ameliorationBonus))) < 1e-6,
                "une épée à +1 gagne " + Textes.nombre(plugin.reglages().ameliorationBonus * 100) + " % d'attaque (attaque " + Textes.nombre(attaque) + ")");

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
                String brut = fr.vorace.item.exemplaire.DonneesBrutes.lire(it, "armor_model_api");
                plugin.exemplaires().ecrire(it, plugin.exemplaires().lire(it));                 // réécrit : la clé doit rester
                String apres = fr.vorace.item.exemplaire.DonneesBrutes.lire(it, "armor_model_api");
                verifier(brut != null && brut.contains("voraceitem:geo/exemple/fer_b.geo.json") && brut.equals(apres) && plugin.exemplaires().lire(it) != null,
                    "armure 3D du fer : custom_data.armor_model_api posé et gardé après réécriture (" + apres + ")");
            }
            // plastron porté : son armure compte dans l'équipement, sa vie max passe par l'attribut du jeu
            ModeleObjet plastron = c.modele("plastron_cuir_cloute");
            if (plastron != null) {
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
                plugin.profilParDefaut().fixerNiveau(p, 1);
                plugin.profilParDefaut().fixerClasses(p, null);
                plugin.equipementChange(p);
                boolean refusee = plugin.equipement().bilan(p).arme() == null;
                plugin.profilParDefaut().fixerNiveau(p, 50);
                plugin.profilParDefaut().fixerClasses(p, "guerrier");
                plugin.equipementChange(p);
                boolean acceptee = plugin.equipement().bilan(p).arme() != null;
                plugin.profilParDefaut().fixerNiveau(p, null);
                plugin.profilParDefaut().fixerClasses(p, null);
                verifier(refusee && acceptee, "la Lame du Veilleur ne compte que pour un guerrier de niveau 40 ou plus");
            }
        } finally {
            p.getInventory().setItemInMainHand(avantMain);
            plugin.equipementChange(p);
        }
    }
}

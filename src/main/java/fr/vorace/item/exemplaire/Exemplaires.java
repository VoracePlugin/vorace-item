package fr.vorace.item.exemplaire;

import fr.vorace.api.VoraceAPI;
import fr.vorace.item.Textes;
import fr.vorace.item.VoraceItem;
import fr.vorace.item.definitions.Catalogue;
import fr.vorace.item.definitions.Materiau.Piece;
import fr.vorace.item.definitions.ModeleObjet;
import fr.vorace.item.definitions.Rarete;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Crée les objets, lit et écrit ce qui appartient à chaque exemplaire, et refait l'affichage (nom, description, apparence). */
public final class Exemplaires {

    public static final String NS = "voraceitem";

    /**
     * Qui a fabriqué l'objet et comment.
     *
     * @param grade        brute, correcte, fine, excellente ou chef_d_oeuvre (null si l'objet n'est pas fabriqué)
     * @param score        score final de fabrication, −1 si inconnu
     * @param exemplaireId identifiant unique choisi par l'appelant (null = tiré au hasard)
     */
    public record Signature(String fabricant, UUID fabricantUuid, String grade, double score, UUID exemplaireId) {
        public static final Signature AUCUNE = new Signature(null, null, null, -1, null);
    }

    private final VoraceItem plugin;
    private final Random hasard = new Random();
    private final Set<String> signales = new HashSet<>();

    private final NamespacedKey kModele = cle("modele"), kRang = cle("rang"), kRebut = cle("rebut"), kPosition = cle("position"),
        kGrade = cle("grade"), kScore = cle("score"), kId = cle("exemplaire_id"), kFabricant = cle("fabricant"),
        kFabricantUuid = cle("fabricant_uuid"), kCree = cle("cree_ts"), kPieces = cle("pieces"), kBonus = cle("bonus"),
        kGemmes = cle("gemmes"), kAmelioration = cle("amelioration"), kDurOrigine = cle("durabilite_origine"),
        kDurMax = cle("durabilite_max"), kDur = cle("durabilite"), kUsure = cle("usure_combat"), kReparations = cle("reparations"),
        kNom = cle("nom");

    public Exemplaires(VoraceItem plugin) {
        this.plugin = plugin;
    }

    public static NamespacedKey cle(String nom) {
        return new NamespacedKey(NS, nom);
    }

    // ------------------------------------------------------------------ lecture

    /** Identifiant du modèle de cet objet, ou null si ce n'est pas un objet de vorace-item. */
    public String modele(ItemStack objet) {
        if (objet == null || objet.getType().isAir() || !objet.hasItemMeta()) return null;
        ItemMeta meta = objet.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(kModele, PersistentDataType.STRING);
    }

    /** Ce qui appartient à cet exemplaire, ou null si ce n'est pas un objet de vorace-item. */
    public Exemplaire lire(ItemStack objet) {
        if (objet == null || objet.getType().isAir() || !objet.hasItemMeta()) return null;
        ItemMeta meta = objet.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer p = meta.getPersistentDataContainer();
        String modele = p.get(kModele, PersistentDataType.STRING);
        if (modele == null) return null;
        Exemplaire ex = new Exemplaire();
        ex.modele = modele;
        ex.rang = p.get(kRang, PersistentDataType.STRING);
        ex.rebut = p.getOrDefault(kRebut, PersistentDataType.BYTE, (byte) 0) != 0;
        ex.position = p.getOrDefault(kPosition, PersistentDataType.DOUBLE, 0.0);
        ex.grade = p.get(kGrade, PersistentDataType.STRING);
        ex.score = p.getOrDefault(kScore, PersistentDataType.DOUBLE, -1.0);
        ex.id = uuid(p.get(kId, PersistentDataType.STRING));
        ex.fabricant = p.get(kFabricant, PersistentDataType.STRING);
        ex.fabricantUuid = uuid(p.get(kFabricantUuid, PersistentDataType.STRING));
        ex.cree = p.getOrDefault(kCree, PersistentDataType.LONG, 0L);
        for (String[] kv : paires(p.get(kPieces, PersistentDataType.STRING))) ex.pieces.put(kv[0], kv[1]);
        for (String[] kv : paires(p.get(kBonus, PersistentDataType.STRING))) {
            try { ex.bonus.put(kv[0], Double.parseDouble(kv[1])); } catch (NumberFormatException ignore) { }
        }
        String gemmes = p.get(kGemmes, PersistentDataType.STRING);
        if (gemmes != null) for (String g : gemmes.split(";")) if (!g.isBlank()) ex.gemmes.add(g);
        ex.amelioration = p.getOrDefault(kAmelioration, PersistentDataType.INTEGER, 0);
        ex.durabiliteOrigine = p.getOrDefault(kDurOrigine, PersistentDataType.INTEGER, -1);
        ex.durabiliteMax = p.getOrDefault(kDurMax, PersistentDataType.INTEGER, -1);
        ex.durabilite = p.getOrDefault(kDur, PersistentDataType.INTEGER, -1);
        ex.usureCombat = p.getOrDefault(kUsure, PersistentDataType.INTEGER, 0);
        ex.reparations = p.getOrDefault(kReparations, PersistentDataType.INTEGER, 0);
        ex.nom = p.get(kNom, PersistentDataType.STRING);
        return ex;
    }

    /** Stats de cet objet (vide si ce n'est pas un objet de vorace-item). */
    public Map<String, Double> stats(ItemStack objet) {
        Exemplaire ex = lire(objet);
        return ex == null ? Map.of() : stats(ex);
    }

    public Map<String, Double> stats(Exemplaire ex) {
        return Calcul.stats(plugin.catalogue(), ex, plugin.reglages().ameliorationBonus);
    }

    // ------------------------------------------------------------------ création

    /**
     * Crée un exemplaire d'un modèle.
     *
     * @param rang      rang de l'exemplaire (null = rang de base du modèle)
     * @param rebut     vrai pour un objet sous le premier rang (fourchette « rebut »)
     * @param position  place dans la fourchette de stats, de 0 à 1
     * @param pieces    arme modulaire : emplacement → pièce ; sinon null
     * @param signature fabricant, grade et score ; {@link Signature#AUCUNE} pour un objet donné ou lâché par un monstre
     * @return l'objet, ou null si le modèle ne peut pas être créé (pièces manquantes ou inconnues)
     */
    public ItemStack creer(ModeleObjet m, Rarete rang, boolean rebut, double position, Map<String, String> pieces,
                           Signature signature, int quantite) {
        return construire(m, rang, rebut, position, pieces, signature, quantite, false);
    }

    /**
     * Objet d'aperçu pour un menu : comme {@link #creer}, au milieu de la fourchette, sans identifiant ni ligne bonus
     * tirée (elles ne sont tirées qu'à la vraie création). Null si les pièces ne conviennent pas.
     */
    public ItemStack apercu(ModeleObjet m, Rarete rang, Map<String, String> pieces) {
        return construire(m, rang, false, 0.5, pieces, Signature.AUCUNE, 1, true);
    }

    private ItemStack construire(ModeleObjet m, Rarete rang, boolean rebut, double position, Map<String, String> pieces,
                                 Signature signature, int quantite, boolean apercu) {
        Catalogue c = plugin.catalogue();
        Exemplaire ex = new Exemplaire();
        ex.modele = m.id();
        ex.rang = (rang == null ? m.rang() : rang).id();
        ex.rebut = rebut;
        ex.position = Math.max(0, Math.min(1, position));
        if (m.kit() != null) {
            if (pieces == null) return null;
            for (String e : m.kit().emplacements()) {
                Piece p = c.piece(pieces.get(e));
                if (p == null || !p.kit().equals(m.id()) || !p.emplacement().equals(e)) return null;
                ex.pieces.put(e, p.id());
            }
        }
        Signature s = signature == null ? Signature.AUCUNE : signature;
        if (!m.empilable()) {
            int durabilite = Calcul.durabiliteOrigine(c, m, ex);
            if (durabilite > 0) ex.durabiliteOrigine = ex.durabiliteMax = ex.durabilite = durabilite;
            if (!apercu) {
                ex.id = s.exemplaireId() == null ? UUID.randomUUID() : s.exemplaireId();
                ex.cree = System.currentTimeMillis();
                ex.fabricant = s.fabricant();
                ex.fabricantUuid = s.fabricantUuid();
                ex.grade = s.grade();
                ex.score = s.score();
                tirerBonus(m, ex, c.raretes().parId(ex.rang));
            }
        }
        ItemStack objet = base(m);
        objet.setAmount(m.empilable() ? Math.max(1, Math.min(objet.getMaxStackSize(), quantite)) : 1);
        ecrire(objet, ex);
        return objet;
    }

    /** Tire les lignes bonus du rang parmi celles que permet le modèle (une seule fois chacune). */
    public void tirerBonus(ModeleObjet m, Exemplaire ex, Rarete rang) {
        ex.bonus.clear();
        if (rang == null || rang.lignesBonus() <= 0 || m.lignesBonus().isEmpty()) return;
        Catalogue c = plugin.catalogue();
        List<String> possibles = new ArrayList<>(m.lignesBonus());
        int palier = Calcul.palier(c, m, ex);
        Map<String, Double> poids = c.poidsBonus(m, ex);
        for (int i = 0; i < rang.lignesBonus() && !possibles.isEmpty(); i++) {
            String id = tirer(possibles, poids);
            double v = c.modificateurs().tirerBonus(id, palier, hasard, rang.multiplicateur() / c.raretes().premier().multiplicateur());
            if (v != 0) ex.bonus.put(id, v);
        }
    }

    /**
     * Ajoute à l'objet une ligne bonus de plus, tirée parmi celles que permet son modèle et qu'il n'a pas encore
     * (passifs de métier du genre « Lame veinée »).
     *
     * @return l'identifiant du modificateur ajouté, ou null si l'objet ne peut plus en recevoir
     */
    public String ajouterBonus(ItemStack objet) {
        Exemplaire ex = lire(objet);
        ModeleObjet m = ex == null ? null : plugin.catalogue().modele(ex.modele);
        if (m == null || m.empilable()) return null;
        Catalogue c = plugin.catalogue();
        List<String> possibles = new ArrayList<>(m.lignesBonus());
        possibles.removeAll(ex.bonus.keySet());
        Rarete rang = c.raretes().parId(ex.rang);
        Map<String, Double> poids = c.poidsBonus(m, ex);
        while (!possibles.isEmpty()) {
            String id = tirer(possibles, poids);
            double v = c.modificateurs().tirerBonus(id, Calcul.palier(c, m, ex), hasard,
                (rang == null ? m.rang() : rang).multiplicateur() / c.raretes().premier().multiplicateur());
            if (v == 0) continue;
            ex.bonus.put(id, v);
            ecrire(objet, ex);
            return id;
        }
        return null;
    }

    /** Tire (et retire de la liste) un modificateur au hasard, pondéré par le matériau (poids 1 par défaut). */
    private String tirer(List<String> possibles, Map<String, Double> poids) {
        double total = 0;
        for (String id : possibles) total += poids.getOrDefault(id, 1.0);
        double r = hasard.nextDouble() * total;
        for (int i = 0; i < possibles.size(); i++) {
            r -= poids.getOrDefault(possibles.get(i), 1.0);
            if (r <= 0) return possibles.remove(i);
        }
        return possibles.remove(possibles.size() - 1);
    }

    private ItemStack base(ModeleObjet m) {
        if (m.apparence().objetVorace() != null) {
            ItemStack v = VoraceAPI.objet(m.apparence().objetVorace(), 1);
            if (v != null) return v;
            if (signales.add(m.id())) plugin.getLogger().warning(m.id() + " : l'objet de vorace-core « " + m.apparence().objetVorace() + " » n'existe pas, apparence de base utilisée");
        }
        Material mat = Material.matchMaterial(m.apparence().materiau());
        if (mat == null || !mat.isItem() || mat.isAir()) {
            if (signales.add(m.id() + ":materiau")) plugin.getLogger().warning(m.id() + " : objet Minecraft inconnu « " + m.apparence().materiau() + " », papier utilisé");
            mat = Material.PAPER;
        }
        return new ItemStack(mat);
    }

    /**
     * Armure en 3D (mod Armor Model API côté joueur) : le mod lit {@code minecraft:custom_data.armor_model_api}. Bukkit
     * garde ensuite cette clé à côté de ses propres données quand l'objet est réécrit.
     */
    private void armure3d(ItemStack objet, ModeleObjet m) {
        if (m.apparence().armure3d() == null) return;
        String tex = m.apparence().textureArmure3d() == null ? m.apparence().armure3d() : m.apparence().textureArmure3d();
        String snbt = "{model:\"" + NS + ":geo/" + m.apparence().armure3d() + ".geo.json\",texture:\"" + NS + ":textures/armor/" + tex + ".png\"}";
        String voulu = NS + ":geo/" + m.apparence().armure3d() + ".geo.json";
        String deja = DonneesBrutes.lire(objet, "armor_model_api");
        if (deja != null && deja.contains(voulu) && deja.contains(tex + ".png")) return;
        try {
            ItemStack copie = DonneesBrutes.poser(objet, "armor_model_api", snbt);
            objet.setItemMeta(copie.getItemMeta());
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (signales.add(m.id() + ":armure3d")) plugin.getLogger().warning(m.id() + " : armure 3D non posée (" + e + ")");
        }
    }

    // ------------------------------------------------------------------ écriture

    /** Écrit les données de l'exemplaire dans l'objet et refait son affichage. Faux si le modèle n'existe plus. */
    public boolean ecrire(ItemStack objet, Exemplaire ex) {
        ModeleObjet m = plugin.catalogue().modele(ex.modele);
        ItemMeta meta = objet.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer p = meta.getPersistentDataContainer();
        p.set(kModele, PersistentDataType.STRING, ex.modele);
        texte(p, kRang, ex.rang);
        if (ex.rebut) p.set(kRebut, PersistentDataType.BYTE, (byte) 1); else p.remove(kRebut);
        p.set(kPosition, PersistentDataType.DOUBLE, ex.position);
        texte(p, kGrade, ex.grade);
        if (ex.score >= 0) p.set(kScore, PersistentDataType.DOUBLE, ex.score); else p.remove(kScore);
        texte(p, kId, ex.id == null ? null : ex.id.toString());
        texte(p, kFabricant, ex.fabricant);
        texte(p, kFabricantUuid, ex.fabricantUuid == null ? null : ex.fabricantUuid.toString());
        if (ex.cree > 0) p.set(kCree, PersistentDataType.LONG, ex.cree); else p.remove(kCree);
        texte(p, kPieces, joindre(ex.pieces));
        texte(p, kBonus, joindre(ex.bonus));
        texte(p, kGemmes, ex.gemmes.isEmpty() ? null : String.join(";", ex.gemmes));
        entier(p, kAmelioration, ex.amelioration, 0);
        entier(p, kDurOrigine, ex.durabiliteOrigine, -1);
        entier(p, kDurMax, ex.durabiliteMax, -1);
        entier(p, kDur, ex.durabilite, -1);
        entier(p, kUsure, ex.usureCombat, 0);
        entier(p, kReparations, ex.reparations, 0);
        texte(p, kNom, ex.nom);
        if (m != null) afficher(meta, m, ex);
        objet.setItemMeta(meta);
        if (m != null) armure3d(objet, m);
        return m != null;
    }

    /** Refait l'affichage d'un objet à partir de ses données (après un changement des fichiers). Faux si ce n'en est pas un. */
    public boolean rafraichir(ItemStack objet) {
        Exemplaire ex = lire(objet);
        return ex != null && ecrire(objet, ex);
    }

    private void afficher(ItemMeta meta, ModeleObjet m, Exemplaire ex) {
        Catalogue c = plugin.catalogue();
        meta.displayName(Textes.objet(Description.nom(c, m, ex)));
        List<Component> lore = new ArrayList<>();
        for (String l : Description.lignes(c, m, ex, stats(ex), id -> plugin.competences() == null ? null : plugin.competences().ligne(id))) {
            lore.add(l.isEmpty() ? Component.empty() : Textes.objet(l));
        }
        meta.lore(lore.isEmpty() ? null : lore);

        // apparence
        if (m.kit() != null) {
            meta.setItemModel(cle("kit/" + m.id()));
            List<String> chaines = new ArrayList<>();
            for (String e : m.kit().emplacements()) chaines.add(ex.pieces.getOrDefault(e, ""));
            chaines.add(ex.rang == null ? "" : ex.rang);
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(chaines);
            meta.setCustomModelDataComponent(cmd);
        } else if (m.apparence().objetVorace() == null) {
            if (m.apparence().dansLePack()) meta.setItemModel(cle("objet/" + m.id()));
            else if (m.apparence().cmd() != null) meta.setCustomModelData(m.apparence().cmd());
        }
        if (m.apparence().brillance() != null) meta.setEnchantmentGlintOverride(m.apparence().brillance());
        org.bukkit.inventory.EquipmentSlot place = place(m.type().emplacement());
        if (m.apparence().equipement() != null && place != null) {    // armure : son dessin porté sur le joueur
            org.bukkit.inventory.meta.components.EquippableComponent eq = meta.getEquippable();
            eq.setSlot(place);
            eq.setModel(cle(m.apparence().equipement()));
            meta.setEquippable(eq);
        } else if (place == org.bukkit.inventory.EquipmentSlot.HEAD && m.apparence().modele() != null && m.kit() == null) {
            // casque en 3D : porté sans dessin d'armure, le jeu montre alors le modèle de l'objet sur la tête
            org.bukkit.inventory.meta.components.EquippableComponent eq = meta.getEquippable();
            eq.setSlot(org.bukkit.inventory.EquipmentSlot.HEAD);
            eq.setModel(null);
            meta.setEquippable(eq);
        }

        // attributs du jeu de base : remplacés (les dégâts et l'armure viennent des stats de vorace-item)
        meta.setAttributeModifiers(null);
        EquipmentSlotGroup groupe = groupe(m.type().emplacement());
        if (m.type().estArme() && m.vitesseAttaque() > 0) {
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(cle("vitesse"), m.vitesseAttaque() - 4.0,
                AttributeModifier.Operation.ADD_NUMBER, groupe));
        } else if (m.type().estArme() || m.type().estArmure()) {
            meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(cle("neutre_" + m.type().emplacement().replace('-', '_')), 0,
                AttributeModifier.Operation.ADD_NUMBER, groupe));
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // durabilité : barre du jeu réglée sur celle de vorace-item ; sans durabilité, l'objet ne s'use pas
        if (!m.empilable()) {
            meta.setMaxStackSize(1);
            if (meta instanceof Damageable d) {
                if (ex.aDurabilite()) {
                    meta.setUnbreakable(false);
                    d.setMaxDamage(ex.durabiliteMax);
                    d.setDamage(Math.max(0, Math.min(ex.durabiliteMax - 1, ex.durabiliteMax - ex.durabilite)));
                } else {
                    meta.setUnbreakable(true);
                    meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
                }
            }
        }

        // temps de recharge visible sur l'objet : celui de la compétence du clic droit (sinon la première)
        String competence = m.competences().getOrDefault("clic-droit", m.competences().values().stream().findFirst().orElse(null));
        double recharge = competence == null || plugin.competences() == null ? 0 : plugin.competences().recharge(competence);
        if (recharge > 0) {
            UseCooldownComponent uc = meta.getUseCooldown();
            uc.setCooldownSeconds((float) recharge);
            uc.setCooldownGroup(cle("objet/" + (ex.id == null ? m.id() : ex.id.toString())));
            meta.setUseCooldown(uc);
        } else {
            meta.setUseCooldown(null);
        }
    }

    public static EquipmentSlotGroup groupe(String emplacement) {
        return switch (emplacement) {
            case "seconde-main" -> EquipmentSlotGroup.OFFHAND;
            case "tete" -> EquipmentSlotGroup.HEAD;
            case "torse" -> EquipmentSlotGroup.CHEST;
            case "jambes" -> EquipmentSlotGroup.LEGS;
            case "pieds" -> EquipmentSlotGroup.FEET;
            default -> EquipmentSlotGroup.MAINHAND;
        };
    }

    /** L'emplacement où l'objet se porte (null : il ne se porte pas). */
    public static org.bukkit.inventory.EquipmentSlot place(String emplacement) {
        return switch (emplacement) {
            case "tete" -> org.bukkit.inventory.EquipmentSlot.HEAD;
            case "torse" -> org.bukkit.inventory.EquipmentSlot.CHEST;
            case "jambes" -> org.bukkit.inventory.EquipmentSlot.LEGS;
            case "pieds" -> org.bukkit.inventory.EquipmentSlot.FEET;
            default -> null;
        };
    }

    // ------------------------------------------------------------------ outils

    private static void texte(PersistentDataContainer p, NamespacedKey k, String v) {
        if (v == null || v.isEmpty()) p.remove(k); else p.set(k, PersistentDataType.STRING, v);
    }

    private static void entier(PersistentDataContainer p, NamespacedKey k, int v, int absent) {
        if (v == absent) p.remove(k); else p.set(k, PersistentDataType.INTEGER, v);
    }

    private static UUID uuid(String s) {
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    private static String joindre(Map<String, ?> m) {
        if (m.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ?> e : m.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            Object v = e.getValue();
            sb.append(e.getKey()).append('=').append(v instanceof Double d ? String.format(Locale.ROOT, "%s", d) : v);
        }
        return sb.toString();
    }

    private static List<String[]> paires(String s) {
        List<String[]> l = new ArrayList<>();
        if (s == null) return l;
        for (String p : s.split(";")) {
            int i = p.indexOf('=');
            if (i > 0) l.add(new String[]{p.substring(0, i), p.substring(i + 1)});
        }
        return l;
    }
}

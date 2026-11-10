package fr.vorace.item.exemplaire;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ce qui appartient à un exemplaire précis d'un objet : son rang, sa place dans la fourchette de stats, sa qualité,
 * son fabricant, ses pièces, ses lignes bonus, ses gemmes, son usure. Tout est gardé dans l'objet lui-même
 * (PersistentDataContainer, espace de noms « voraceitem ») : vorace-core le conserve quand il met l'objet à jour.
 */
public final class Exemplaire {

    /** Identifiant du modèle (objets/*.yml). */
    public String modele;
    /** Identifiant du rang de rareté. */
    public String rang;
    /** Vrai pour un objet Brut de matériau Commun : ses stats sont dans la fourchette « rebut », sous le Commun. */
    public boolean rebut;
    /** Place dans la fourchette de stats du rang, de 0 (bas) à 1 (haut). */
    public double position;
    /** Grade de fabrication (brute, correcte, fine, excellente, chef_d_oeuvre), ou null pour un objet non fabriqué. */
    public String grade;
    /** Score final de fabrication (0 à 100), ou −1. */
    public double score = -1;
    /** Identifiant unique de l'exemplaire (absent sur les objets empilables). */
    public UUID id;
    public String fabricant;
    public UUID fabricantUuid;
    /** Date de création (millisecondes), 0 si inconnue. */
    public long cree;
    /** Arme modulaire : emplacement → identifiant de pièce (« fer/lame_droite »). */
    public final Map<String, String> pieces = new LinkedHashMap<>();
    /** Lignes bonus tirées : modificateur → valeur. */
    public final Map<String, Double> bonus = new LinkedHashMap<>();
    /** Gemmes serties (identifiants de modèles). */
    public final List<String> gemmes = new ArrayList<>();
    /** Niveau d'amélioration (+1, +2...). */
    public int amelioration;
    /** Durabilité : d'origine, maximale actuelle (elle baisse à chaque réparation), restante. −1 = l'objet ne s'use pas. */
    public int durabiliteOrigine = -1;
    public int durabiliteMax = -1;
    public int durabilite = -1;
    /** Durabilité perdue en combat depuis la dernière réparation. */
    public int usureCombat;
    public int reparations;
    /** Nom donné à l'objet (sinon celui du modèle). */
    public String nom;

    public boolean aDurabilite() { return durabiliteMax > 0; }

    /** Vrai si l'objet est cassé : il reste dans l'inventaire, mais ses stats ne comptent plus tant qu'il n'est pas réparé. */
    public boolean casse() { return aDurabilite() && durabilite <= 0; }

    public Exemplaire copie() {
        Exemplaire e = new Exemplaire();
        e.modele = modele; e.rang = rang; e.rebut = rebut; e.position = position; e.grade = grade; e.score = score;
        e.id = id; e.fabricant = fabricant; e.fabricantUuid = fabricantUuid; e.cree = cree;
        e.pieces.putAll(pieces); e.bonus.putAll(bonus); e.gemmes.addAll(gemmes);
        e.amelioration = amelioration; e.durabiliteOrigine = durabiliteOrigine; e.durabiliteMax = durabiliteMax;
        e.durabilite = durabilite; e.usureCombat = usureCombat; e.reparations = reparations; e.nom = nom;
        return e;
    }
}

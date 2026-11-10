# vorace-item

Des **objets RPG** pour serveurs Paper : stats, rareté, armes assemblées pièce par pièce, forge, atelier, compétences
d'armes et butin des monstres. Fait partie de [VoracePlugin](https://github.com/VoracePlugin) et s'appuie sur
[vorace-core](https://github.com/VoracePlugin/vorace-core) (plugin `Vorace`) pour le pack de ressources.
C'est le rôle de MMOItems, en libre et gratuit.

> **Code écrit par une IA.** Comme vorace-core, vorace-item est écrit par Claude (l'IA d'Anthropic) ; ORA et Antoine
> testent et décident de ce qui est ajouté.
>
> **Version 0.9.** Tout ce qui est décrit ici est codé et essayé sur un vrai serveur Paper 1.21.4 avec un faux
> joueur. Ce qui reste à voir avec un vrai joueur : la tenue des armes modulaires en main (réglages d'affichage) et
> l'équilibrage des chiffres.

## Installation

1. Mets `Vorace-x.y.z.jar` ([vorace-core](https://github.com/VoracePlugin/vorace-core), 1.2.1 ou plus) et
   `VoraceItem-x.y.z.jar` dans `plugins/`. Les deux `.jar` sont dans l'onglet
   [Actions](https://github.com/VoracePlugin/vorace-item/actions) (« Compilation », fichier `VoraceItem`).
2. Il faut **Paper 1.21.4 ou plus** : les armes assemblées et le temps de recharge visible sur les objets
   n'existent pas avant.
3. Lance le serveur. Le dossier `plugins/VoraceItem` est créé avec des exemples : une épée modulaire en cuivre, fer
   et acier, une dague, un bâton, deux pièces d'armure, trois armures complètes (hoplite de cuivre, chevalier de
   fer, gardien d'acier : casque en 3D, corps dessiné comme les armures du jeu), deux gemmes, une pierre
   d'amélioration.
4. En jeu : `/vitem catalogue` pour prendre les objets d'exemple, `/vitem forge` pour forger, `/vitem atelier` pour
   réparer, améliorer et sertir.

**Arcs** : un arc dessiné (`apparence.texture: <chemin>`) se bande en main comme celui du jeu si
`ressources/textures/<chemin>_tension_0.png`, `_1.png` et `_2.png` existent.

**Casques en 3D** : un casque (`type: casque`) avec `apparence.modele` et sans `apparence.equipement` est porté
sur la tête comme son modèle (comme une citrouille), sans dessin d'armure. Le modèle se fait dans Blockbench, l'avant
de la tête vers le nord, la tête du joueur au centre (16 unités = 10 pixels du joueur) ; le réglage d'affichage
`head` le place. Les armures complètes font comme les packs des serveurs : casque en 3D, reste en dessin d'armure de
16 pixels par bloc (`ressources/equipement/<chemin>/humanoid.png`, 64 x 32).

**Armures en 3D (mod côté joueur)** : `apparence.armure-3d: <chemin>` (et `texture-armure-3d`) pose sur l'objet le
composant lu par le mod [Armor Model API](https://github.com/FabricExtras/ArmorModelAPI) ; le modèle
`ressources/armure3d/<chemin>.geo.json` (format Bedrock, os `armorHead`, `armorBody`…) et sa texture vont dans le pack.
Garder un `equipement` pour les joueurs sans le mod. Attention : le mod n'existe pas en 1.21.4 (1.21.1, 1.21.11, 26.x).

vorace-item écrit sa partie du pack de ressources dans `plugins/Vorace/pack/sources/voraceitem`, que vorace-core
fusionne tout seul avec les autres packs. Si tu as changé les `sources` dans `plugins/Vorace/config.yml`, garde
`plugins/Vorace/pack/sources/*`.

## Ce que fait vorace-item

| | |
|---|---|
| **Rareté** | Les 7 rangs de Babel (Commun, Peu commun, Rare, Épique, Légendaire, Mythique, Divin) : couleur du nom, lignes bonus, emplacements de gemmes. |
| **Stats** | Attaque physique, Puissance magique, Armure physique, Armure magique, et les 38 modificateurs de Babel (critique, pénétration, vol de vie, vitesse...). |
| **Exemplaires** | Chaque objet a son rang, sa place dans la fourchette de stats, sa qualité, son fabricant, ses gemmes, son usure. |
| **Armes modulaires** | Une arme = une pièce par emplacement (lame, garde, poignée, pommeau), chacune dans le matériau de son choix. Le pack les assemble en un seul objet : toutes les combinaisons existent sans être dessinées une par une. |
| **Combat** | La formule de Babel : `Dégâts = Puissance² ÷ (Puissance + Défense)`, scale sur deux stats, critique physique, esquive. |
| **Durabilité** | Un objet usé est « cassé », pas détruit. Il se répare, et s'use un peu pour de bon à chaque réparation. |
| **Forge** | Menu de fabrication : choix des pièces, matériaux demandés, qualité du résultat. |
| **Atelier** | Réparer, améliorer (+1, +2...) avec une pierre, sertir une gemme. |
| **Compétences d'armes** | Un clic lance la compétence : mana ou stamina, temps de recharge affiché sur l'objet. |
| **Butin** | Les monstres lâchent des armes assemblées avec les pièces des matériaux de leur étage. |

## Les fichiers

Tout se règle dans `plugins/VoraceItem`, puis `/vitem recharger`. Chaque fichier commence par son mode d'emploi.

| Fichier | Contenu |
|---|---|
| `config.yml` | Combat, mana et stamina, qualité, forge, atelier, durabilité, amélioration, butin. |
| `raretes.yml` | Les rangs : couleur, multiplicateur, lignes bonus, gemmes. |
| `modificateurs.yml` | Les stats et modificateurs : nom affiché, valeurs des lignes bonus. |
| `types.yml` | Les types d'objets (épée, dague, bâton, plastron, gemme...) et leurs valeurs par défaut. |
| `objets/*.yml` | Les **modèles** d'objets. |
| `materiaux/*.yml` | Les matériaux de forge et leurs **pièces**. |
| `competences.yml` | Les compétences d'armes. |
| `butins.yml` | Les tables de butin et les monstres qui les utilisent. |
| `ressources/modeles`, `ressources/textures` | Les modèles 3D (`.json` de Blockbench) et les textures (`.png`). |

### Un modèle d'objet

```yaml
dague_de_chasse:
  nom: "Dague de chasse"
  type: dague                 # voir types.yml : emplacement, scales, vitesse, lignes bonus par défaut
  rang: peu_commun            # rang de base : le modèle n'existe qu'à ce rang, celui du dessous ou celui du dessus
  etage: 4                    # étage du donjon (son palier fixe la force des lignes bonus)
  stats:
    attaque: 16               # valeur au rang de base, en bas de la fourchette
  modificateurs:
    critical-strike-chance: 4 # modificateurs fixes
  durabilite: 180
  conditions: { niveau: 5, classes: [assassin] }
  competences:
    clic-droit: ruee          # aussi : clic-gauche, accroupi-clic-droit, accroupi-clic-gauche
  gemmes: 1
  recette: { IRON_INGOT: 1, LEATHER: 1, STICK: 1 }   # la forge sait le fabriquer
  description:
    - "Courte, légère, faite pour finir le travail."
```

L'apparence se choisit avec `apparence:` : `materiau` (un objet du jeu de base), `objet-vorace` (un objet de
`plugins/Vorace/objets`, avec sa texture ou son modèle 3D), `modele` (un `.json` de `ressources/modeles`) ou
`texture` (un `.png` de `ressources/textures`).

### Rangs et fourchettes de stats

Chaque stat de rang (Attaque, Puissance, Armure) a une **fourchette par rang**. Les fourchettes de deux rangs voisins
se touchent : le haut du Rare est le bas de l'Épique. La **qualité** de fabrication place l'objet dans sa fourchette.

Exemple : une dague à 16 d'attaque en Peu commun.

| Rang | Rebut | Commun | Peu commun | Rare | Épique | Légendaire | Mythique | Divin |
|---|---|---|---|---|---|---|---|---|
| Multiplicateur | 0,85 | 1 | 1,1 | 1,2 | 1,35 | 1,5 | 1,7 | 1,9 (jusqu'à 2,1) |
| Attaque | 12,4 à 14,5 | 14,5 à 16 | **16 à 17,5** | 17,5 à 19,6 | | | | |

Un modèle ne s'éloigne jamais de plus d'un rang de son rang de base : cette dague n'existe qu'en Commun, Peu commun
ou Rare. Le « rebut » est la fourchette des objets Bruts faits avec un matériau Commun.

### Qualité de fabrication

Le score de fabrication (0 à 100) donne le grade, le rang et la place dans la fourchette, selon le cahier des métiers
de Babel :

| Grade | Score | Effet |
|---|---|---|
| Brute | 0 à 29 | Rang du dessous (ou rebut). |
| Correcte | 30 à 54 | Rang du matériau, bas de la fourchette. |
| Fine | 55 à 69 | Rang du matériau. |
| Excellente | 70 à 84 | Rang du matériau, haut de la fourchette. |
| Chef-d'œuvre | 85 à 100 | Haut de la fourchette ; peut gagner un rang (jamais au-dessus de Légendaire). |

Un matériau Légendaire ou Mythique demande au moins 70 pour garder son rang, un Divin 85. La place suit le score en
continu, avec un petit aléa : aucune marche entre deux grades.

### Armes modulaires

Un modèle `modulaire` est un kit : la liste de ses emplacements. Les pièces sont dans `materiaux/*.yml`.

```yaml
# objets/epees.yml
epee:
  nom: "Épée"
  type: epee
  modulaire:
    emplacements: [lame, garde, poignee, pommeau]
    rang-max: epique                 # au-dessus : des modèles complets faits à la main
    nom: "<type> <materiau>"         # « Épée de fer » : matériau de la première pièce
    affichage: { ... }               # réglages « display » communs à toutes les pièces
```

```yaml
# materiaux/fer.yml
fer:
  nom: "Fer"
  complement: "de fer"
  etage: 5                           # palier 1 ; aux étages 11 à 20, la base des pièces vaut x 1,12
  pieces:
    lame_droite:
      kit: epee
      emplacement: lame
      nom: "Lame droite"
      modele: fer/lame_droite        # ressources/modeles/fer/lame_droite.json
      base: { attaque: 15 }
      cout: { IRON_INGOT: 2 }
      durabilite: 140
```

- Les stats d'une arme sont la **somme des bases de ses pièces**, chacune multipliée par le palier de son matériau.
- **Dessiner une pièce** : dans Blockbench (« Java Block/Item »), toutes les pièces d'un kit dans le même repère,
  comme si l'arme entière était dans le fichier. Les pièces d'exemple sont debout, centrées en x = 8 et z = 8, la
  poignée vers y = 0. Exporte le `.json` dans `ressources/modeles` et la texture dans `ressources/textures`. Ne mets
  pas de réglages « display » dans la pièce : ceux du kit (`affichage`) sont appliqués à toutes.
- `gravures:` (facultatif, dans `modulaire`) : un modèle posé par-dessus l'arme selon son rang (`rare: chemin`).
- Les exemples (trois ensembles : épée courte de cuivre, épée d'armes de fer, épée longue d'acier) sont dessinés par
  [outils/exemples-pieces.py](outils/exemples-pieces.py) : un dessin de 128 pixels par pièce, découpé en petits cubes
  d'épaisseur variable. Toutes les pièces partagent la même grille, donc elles se mélangent entre ensembles.

Dans le pack, le kit devient un objet « composite » qui choisit une pièce par emplacement
(`assets/voraceitem/items/kit/epee.json`). L'arme porte le nom de ses pièces dans `custom_model_data`.

#### Les pièces en objets

Une pièce peut aussi exister seule, comme objet d'inventaire : une « Lame droite de fer », une « Garde en croissant
d'acier ». On fabrique chaque pièce à part, puis on monte l'arme avec une pièce par emplacement.

- **Rien n'est écrit sur une pièce**, à part son nom : ni stats, ni qualité. On ne peut pas trier ses pièces et jeter
  les moins bonnes : il faut finir l'arme pour savoir ce qu'elle vaut.
- Chaque pièce a pourtant **sa propre qualité** : un score de 0 à 100, gardé caché dans l'objet. Une pièce ne
  s'empile pas (deux pièces empilées trahiraient qu'elles ont le même score).
- L'arme montée prend la **moyenne des scores** de ses pièces. Ce score donne son grade, son rang et sa place dans la
  fourchette, avec les mêmes règles que la qualité de fabrication. C'est sur l'arme que tout est écrit.
- Une arme montée ne se démonte pas.
- Dans le pack, chaque pièce a son objet (`assets/voraceitem/items/piece/<matériau>/<pièce>.json`) : le modèle de la
  pièce, recentré et agrandi dans sa case d'inventaire.

vorace-item ne décide pas **comment** une pièce se fabrique : c'est le plugin des métiers qui appelle
`VoraceItemAPI.creerPiece` quand la pièce est faite, puis `VoraceItemAPI.assemblerPieces` quand le joueur monte son
arme. Pour essayer sans lui : `/vitem piece <joueur> <pièce> [score]` puis `/vitem monter <joueur>`.

### Combat

```
PUISSANCE = (Attaque ou Puissance de l'arme + scale1 × stat1 + scale2 × stat2) × multiplicateur de compétence
DÉFENSE   = Résistance + Armure du même type
DÉGÂTS    = PUISSANCE² ÷ (PUISSANCE + DÉFENSE)
```

Ordre : esquive (20 % au plus) → Puissance → Défense → formule → critique × 1,25 (physique seulement) → réduction
des dégâts → arrondi, 1 au minimum. Lettres de scale : S 1,5 ; A 1,2 ; B 1,0 ; C 0,75 ; D 0,5 ; E 0,25.
Frapper sans attendre la recharge de l'arme donne moins de dégâts, comme dans le jeu de base.

- **Stats des joueurs** (FOR, INT, CON, AGI, niveau, classe, résistances) : elles viennent du plugin des classes
  (voir plus bas). En attendant, ce sont les valeurs de `profil-par-defaut` (`config.yml`), réglables joueur par
  joueur : `/vitem profil Antoine force 50`, `/vitem profil Antoine classes guerrier,spadassin`.
- **Monstres** : leur défense est donnée par un autre plugin, ou par deux valeurs écrites sur le monstre
  (`voraceitem:resistance_physique`, `voraceitem:resistance_magique`), sinon par leur armure du jeu de base. Quand un
  monstre frappe un joueur, sa Puissance est son dégât du jeu de base × `combat.attaque-monstres`.
- Un objet ne compte que **porté à sa place**, **non cassé**, et si le joueur remplit ses **conditions**.
- **Avec le jeu de base** : par défaut, la formule ne touche que les coups portés avec une arme de vorace-item et les
  coups reçus par un joueur qui porte au moins un objet de vorace-item ; une épée en diamant ou une armure en fer
  gardent leurs règles. Sur un serveur où tout l'équipement vient de vorace-item (Babel), mets
  `combat.formule-partout: true` : tous les coups suivent la formule, même à mains nues.
- `combat.actif: false` : vorace-item ne touche plus aux dégâts.
- Les modificateurs que vorace-item applique lui-même sont notés `[appliqué]` dans `modificateurs.yml` ; les autres
  sont additionnés et laissés aux autres plugins.

### Durabilité, atelier

- L'arme s'use à chaque coup porté, l'armure à chaque coup reçu. À 0, l'objet est **cassé** : il reste dans
  l'inventaire, ses stats ne comptent plus. À la mort, armes et armures perdent 40 % de leur durabilité restante.
- **Réparer** remet la durabilité au maximum et retire pour de bon 0,5 % de la durabilité d'origine. Sous la moitié
  de sa durabilité d'origine, un objet ne se répare plus.
- **Améliorer** : une pierre d'amélioration (type `pierre`) fait passer l'objet à +1, +2... (+3 % de stats de rang
  par niveau, chances réglables). La pierre est consommée même en cas d'échec.
- **Sertir** : une gemme (type `gemme`) ajoute ses modificateurs à l'objet, dans un emplacement libre.
- L'enclume, la meule, la table d'enchantement et l'établi du jeu de base ne touchent pas aux objets de vorace-item.

### Forge

`/vitem forge` (ou un bloc : `forge.blocs` dans `config.yml`) liste les armes modulaires et les modèles qui ont une
`recette`. Pour une arme modulaire, le joueur choisit chaque pièce ; le menu montre l'aperçu et les matériaux.

La forge de vorace-item est le **bouton « Fabriquer »** du cahier des métiers de Babel : le résultat d'un débutant
(45 % Brute, 50 % Correcte, 5 % Fine), jamais au-dessus du rang `forge.rang-max` (Rare). Le travail à la main (gestes,
mini-jeux, maîtrise) appartient au plugin des métiers, qui crée ses objets par l'API ci-dessous.

### Compétences d'armes

```yaml
entaille:
  nom: "Entaille"
  recharge: 6          # secondes, affichées sur l'objet
  stamina: 15
  effets:
    - { type: son, son: entity.player.attack.sweep }
    - { type: degats, cible: cone, rayon: 3.5, angle: 100, multiplicateur: 1.6 }
```

Effets : `degats`, `projectile`, `ruee`, `saut`, `soin`, `effet` (de potion), `son`, `particules`, `message`,
`commande`. Ce sont des compétences simples, attachées à un objet ; celles des classes viendront du plugin des classes.
Les objets d'exemple n'ont pas de compétence : celles de Babel viendront avec la liste d'Antoine.

### Butin

```yaml
tables:
  etage_5:
    chance: 0.08
    rangs: { commun: 90, peu_commun: 10 }
    entrees:
      - { poids: 10, kit: epee, materiaux: [cuivre, fer] }   # arme assemblée avec les pièces de ces matériaux
      - { poids: 1, modele: dague_de_chasse }
      - { poids: 4, objet: IRON_NUGGET, quantite: 2-5 }
mobs:
  "vorace:kobold": etage_5    # un mob de vorace-mob
  skeleton: etage_5           # un monstre du jeu de base
```

Seuls les monstres tués par un joueur lâchent ce butin. La Chance du joueur augmente la chance de 1 % par point.

## Commandes

| Commande | Rôle | Permission |
|---|---|---|
| `/vitem forge` | Ouvrir la forge | `voraceitem.forge` (tous) |
| `/vitem atelier` | Réparer, améliorer, sertir | `voraceitem.atelier` (tous) |
| `/vitem donner <joueur> <modèle> [rang] [place 0-100] [quantité]` | Donner un objet | `voraceitem.admin` |
| `/vitem assembler <joueur> <kit> <hasard\|pièce,pièce,...> [rang] [place]` | Donner une arme modulaire (`hasard:fer+cuivre` limite les matériaux) | `voraceitem.admin` |
| `/vitem piece <joueur> <pièce> [score 0-100]` | Donner une pièce seule (`fer/lame_droite`) ; son score de qualité reste caché | `voraceitem.admin` |
| `/vitem monter <joueur> [kit]` | Monter l'arme avec les pièces de l'inventaire du joueur (la première trouvée par emplacement) | `voraceitem.admin` |
| `/vitem catalogue` | Tous les modèles, un clic pour en prendre un | `voraceitem.admin` |
| `/vitem info` | Données de l'objet tenu en main, avec ses fourchettes | `voraceitem.admin` |
| `/vitem reparer`, `/vitem nommer <nom>` | Réparer ou nommer l'objet tenu en main | `voraceitem.admin` |
| `/vitem profil <joueur> [stat valeur \| classes a,b \| raz]` | Stats d'essai d'un joueur | `voraceitem.admin` |
| `/vitem butin <table> [joueur]` | Tirer le butin d'une table | `voraceitem.admin` |
| `/vitem competence <compétence> [joueur]` | Lancer une compétence | `voraceitem.admin` |
| `/vitem pack`, `/vitem recharger` | Refaire le pack, relire les fichiers | `voraceitem.admin` |
| `/vitem essai` | Essai automatique | `voraceitem.admin` |

`voraceitem.forge.tout` permet de forger au-dessus de `forge.rang-max`. `voraceitem.classe.<classe>` donne une classe
au joueur pour les conditions des objets (tant qu'aucun plugin de classes ne le fait).

## Pour les autres plugins

Dépendance (JitPack) : `compileOnly("com.github.VoracePlugin:vorace-item:<version>")`, et `depend: [VoraceItem]`.

```java
// plugin des métiers : un objet fabriqué à la main, avec son score
Qualite.Resultat q = VoraceItemAPI.qualite(82, "rare", 0.33, 0, new Random());
ItemStack epee = VoraceItemAPI.assembler("epee", pieces, q.rang().id(), q.position(), null,
    new Signature(joueur.getName(), joueur.getUniqueId(), q.grade().id(), q.score(), null));

// pièces fabriquées une à une, puis montées : l'arme prend la moyenne des scores de ses pièces
ItemStack lame = VoraceItemAPI.creerPiece("fer/lame_droite", 78);          // le score reste caché
Map<String, Integer> cout = VoraceItemAPI.coutPiece("fer/lame_droite");          // iron_ingot → 2
Pieces.PieceObjet lue = VoraceItemAPI.piece(lame);                               // identifiant, score caché
Pieces.Assemblage montage = VoraceItemAPI.assemblerPieces("epee", List.of(lame, garde, poignee, pommeau),
    "commun", 0, 0, joueur, new Random());
if (montage.objet() != null) { /* retirer les quatre pièces, donner montage.objet() */ } else joueur.sendMessage(montage.erreur());

double[] fourchette = VoraceItemAPI.fourchette("dague_de_chasse", null, "attaque", "rare");   // ou « rebut »
Exemplaire ex = VoraceItemAPI.exemplaire(objet);       // rang, qualité, fabricant, pièces, gemmes, usure de combat
double critique = VoraceItemAPI.total(joueur, "critical-strike-chance");
VoraceItemAPI.frapper(joueur, cible, 1.8, null, "ma_competence");   // un coup avec la formule de dégâts
```

- **`ProfilJoueur`** et **`RessourcesJoueur`** : le plugin des classes fournit les stats, le niveau, les classes, le
  mana et la stamina des joueurs en s'enregistrant auprès du serveur
  (`Bukkit.getServicesManager().register(ProfilJoueur.class, ...)`).
- **`VoraceItemAPI.defenseMonstres(...)`** : le plugin des monstres fournit leur défense.
- Événements : `VoraceItemDegatsEvent` (dégâts modifiables, type d'attaque et élément du coup : c'est là que se
  branchent les altérations), `VoraceItemCompetenceEvent`, `VoraceItemCreeEvent` (forge, butin),
  `VoraceItemRepareEvent` (avec l'usure de combat, pour l'apprentissage des recettes par la réparation).
- Les données d'un exemplaire sont dans l'objet (`PersistentDataContainer`, espace de noms `voraceitem` :
  `modele`, `rang`, `position`, `grade`, `score`, `exemplaire_id`, `fabricant`, `pieces`, `bonus`, `gemmes`,
  `amelioration`, `durabilite`...).

## Compiler

```
gradle build tests                                                  # vorace-core pris sur JitPack
gradle build tests -PvoraceCore=../vorace-core/build/libs/Vorace-1.2.1.jar
```

Java 21, API Paper 1.21.4. Sans accès à repo.papermc.io : `./construire-local.sh <classpath> <Vorace-*.jar> tests`.
Les tests sans serveur sont dans `src/test` (tâche `tests`) ; l'essai sur un vrai serveur Paper avec un faux joueur
est [outils/essai-serveur.sh](outils/essai-serveur.sh), lancé à chaque envoi par GitHub Actions.

## Licence

MIT.

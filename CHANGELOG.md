# Journal des changements

## 0.13.1 (novembre 2026) : retours de l'essai en jeu

- **Arme en seconde main** : sans réglage pour la main gauche, le jeu reprenait celui de la main droite en miroir,
  et une lame courbe partait vers l'avant. vorace-item écrit maintenant la main gauche lui-même (rotation verticale
  inversée), pour les kits d'armes modulaires et les objets qui ont des réglages d'affichage. Un réglage de main
  gauche écrit dans le fichier est gardé tel quel.
- **Icône à plat pour un objet en 3D** : un objet qui a un `modele` et une `texture` montre le modèle 3D partout
  (sur la tête, en main, au sol) et la texture dans l'inventaire. Les casques de cuivre et d'acier d'exemple ont leur
  icône (`cuivre_casque.png`, `acier_casque.png`, tirées de la face avant du casque par `outils/exemples-armures.py`).
- **`apparence.affichage`** : réglages d'affichage d'un objet simple (même format que `modulaire.affichage`), pour le
  grandir ou le tourner en main. Les images de tension d'un arc les reprennent. Les arcs d'exemple sont 25 % plus
  grands en main que l'arc du jeu (leur dessin occupe moins de place dans son image).
- **Épées d'exemple trop grandes à la 1re personne** (elles sortaient de l'écran) : 30 % plus petites
  (`firstperson_righthand` du kit : échelle 1,3 → 0,9), la poignée reste dans la main.

## 0.13.0 (novembre 2026) : vorace-item ne fait plus que créer les objets

vorace-item crée les objets et leur donne leurs stats, rien d'autre. Ce qui ne crée pas d'objets part dans les
plugins qui s'en chargeront (plugin cœur pour le combat et les compétences, Matsuri Métier pour la fabrication,
plugin des monstres ou du donjon pour le butin).

**Retiré**
- **Compétences d'armes** : `competences.yml`, `/vitem competence`, `VoraceItemAPI.lancerCompetence`,
  `VoraceItemCompetenceEvent`, la clé `competences` des modèles (signalée au chargement si elle reste) et le temps de
  recharge affiché sur l'objet (retiré des anciens objets à leur mise à jour).
- **Combat** : formule de dégâts, esquive, critique, vol de vie, renvoi de dégâts, défense des monstres, mana et
  stamina (section `combat` et `ressources` de `config.yml`, `VoraceItemAPI.frapper`, `defenseMonstres`,
  `ressources()`, `VoraceItemDegatsEvent`, `DefenseMonstre`, `RessourcesJoueur`). Les stats restent sur les objets et
  se lisent avec `VoraceItemAPI.total` ; dans `modificateurs.yml`, `[combat]` marque ce qui revient au plugin du combat.
- **Forge et atelier** : `/vitem forge`, `/vitem atelier`, leurs blocs, `voraceitem.forge`, `voraceitem.atelier`,
  `voraceitem.forge.tout`, les sections `forge`, `atelier`, `gemmes` et les chances d'amélioration. Réparer reste dans
  l'API (`VoraceItemAPI.reparer`) ; amélioration et gemmes se posent dans les données de l'objet par un autre plugin.
- **Butin** : `butins.yml`, `/vitem butin`, `VoraceItemAPI.tirerButin`, `VoraceItemCreeEvent`.
- `ProfilJoueur` ne demande plus que le niveau et les classes (conditions des objets) ; `/vitem profil` ne règle plus
  que ceux-là.
- Les anciens `competences.yml` et `butins.yml` de `plugins/VoraceItem` ne sont plus lus (on peut les supprimer).

**Changé**
- **Usure** : chaque fois que le jeu userait un objet de vorace-item (coup porté, coup reçu par l'armure, flèche
  tirée), l'objet perd à la place sa durabilité de vorace-item (`durabilite.usure-par-utilisation`, ancien
  `usure-par-coup` encore lu). Avant, c'était le combat de vorace-item qui l'usait. La perte à la mort reste.
- `/vitem` est réservé aux administrateurs (toutes ses sous-commandes l'étaient déjà, sauf forge et atelier).

**Corrigé**
- **Vie perdue à la reconnexion** : à la déconnexion, vorace-item retirait le bonus de vie max de l'équipement avant
  la sauvegarde du joueur ; en revenant, le jeu bornait sa vie au maximum sans bonus. Le bonus reste maintenant sur
  le joueur (il est recalculé toutes les demi-secondes) ; il n'est retiré que si vorace-item est arrêté seul, pas à
  l'arrêt du serveur.

## 0.12.1 (novembre 2026)

- `VoraceItemAPI.ressources()` : le mana et la stamina des joueurs pour les autres plugins (potions des métiers) ;
  `RessourcesJoueur.rendreStamina`.

## 0.12.0 (novembre 2026) : objets de bois de l'Ébéniste

- **Arcs qui se bandent** : un objet dessiné (`apparence.texture`) qui a ses trois images de tension
  (`<texture>_tension_0`, `_1`, `_2`) reçoit la définition de l'arc du jeu (il se bande en main, trois images selon la
  durée du tir).
- Nouveau type `munition` (s'empile, matériau du jeu : flèche).
- Essences de bois d'exemple (sans pièces d'épée) : if, frêne, chêne, avec leurs lignes bonus favorites.
- Objets d'exemple : arcs courts d'if, de frêne et de chêne, arc long d'if, bâtons de chêne et de frêne (cristal
  d'améthyste), flèche. Dessins : `outils/exemples-bois.py` (32 x 32, style de l'arc du jeu).

## 0.11.2 (novembre 2026) : armures en 3D par le mod Armor Model API

- Nouvelle apparence `armure-3d` (et `texture-armure-3d`) : l'objet reçoit `minecraft:custom_data.armor_model_api`
  (modèle `.geo.json` et texture), que lit le mod côté joueur [Armor Model API](https://github.com/FabricExtras/ArmorModelAPI).
  Les fichiers vont dans `ressources/armure3d/` et sont écrits dans le pack (`assets/voraceitem/geo/`,
  `assets/voraceitem/textures/armor/`). Le dessin d'armure (`equipement`) reste celui des joueurs sans le mod.
- Armure de fer d'exemple : le modèle d'essai de Babel (`exemple/fer_b` : épaulières et cape ; `fer_a` sans) sur les
  quatre pièces ; casque de secours dessiné sur la tête (cervelière à nasal, camail), plus de modèle 3D porté pour
  le casque de fer.

## 0.11.1 (novembre 2026) : armures refaites, casques en 3D

- **Casques en 3D** : un casque avec un modèle (`apparence.modele`) et sans dessin d'armure se porte sur la tête comme
  son modèle (objet de la tête sans dessin d'armure : le jeu affiche le modèle). L'emplacement du dessin d'armure est
  maintenant toujours fixé par le type d'objet (tête, torse, jambes, pieds).
- **Armures d'exemple refaites** comme celles des packs des serveurs : casque sculpté en 3D (16 pixels par bloc),
  corps dessiné pixel par pixel comme les armures du jeu. Cuivre : hoplite (casque corinthien à crête de crin rouge,
  cuirasse musclée, ptéryges, cnémides). Fer : chevalier de la Tour (grand heaume à croix de laiton et panache,
  haubert, cotte d'armes rouge à la tour). Acier : gardien (heaume à bec et ailes, plates d'acier noirci bordées d'or,
  drap bleu nuit).
- Aperçu calculé : `python3 outils/exemples-armures.py apercu.png` (mannequin vu de face et de dos, gros plan des casques).

## 0.11.0 (novembre 2026) : lignes bonus orientées par le matériau, armures portées

- **Lignes bonus orientées par le matériau** : elles restent tirées au hasard dans la liste du type d'objet, mais
  `lignes-bonus` d'un matériau (`materiaux/*.yml`, modificateur → poids) fait sortir certaines plus souvent. Une arme
  prend les poids du matériau de sa lame ; un objet simple, ceux de son champ `materiau`. Les pièces d'exemple n'ont
  plus de modificateur fixe : cuivre (vitesse, critique, déplacement, esquive), fer (dégâts physiques, usure, armure,
  vie), acier (pénétration, dégâts critiques, réduction de dégâts, ténacité).
- **Armures portées** : `apparence.equipement: <chemin>` donne à une armure son dessin sur le joueur
  (`ressources/equipement/<chemin>/humanoid.png` et `humanoid_leggings.png`, disposition des armures du jeu) ;
  vorace-item l'écrit dans le pack (`equipment/`, `textures/entity/equipment/`, Minecraft 1.21.4) et le pose sur
  l'objet (composant « equippable »).
- **Armures d'exemple** (`outils/exemples-armures.py`, aperçu `outils/apercu-armures.png`) : cuivre (casque à nasal
  et coiffe de cuir, gambison matelassé et plastron de lamelles), fer (chapel de fer et camail, cotte de mailles,
  brigandine rivetée), acier (heaume à visière et bavière, plates bordées d'or, braconnière, épaulières à lames).
  Casque, cuirasse, jambières et bottes pour chaque matériau ; elles se forgent avec Matsuri Métier.

## 0.10.3 (novembre 2026) : stats rondes, modificateurs logiques, cimeterre dans le bon sens

- **Stats de rang arrondies à l'unité** (attaque physique, puissance magique, armures) : « 19 » au lieu de « 18,9 ».
  Les objets déjà fabriqués suivent, puisque leurs stats sont recalculées depuis les fichiers.
- **Modificateurs logiques** : une arme ne donne plus d'armure. Exemples : la garde droite donne +5 % de dégâts
  critiques (au lieu de +2 d'armure) ; la lame large frappe plus fort (+3 dégâts physiques) mais plus lentement
  (−6 % de vitesse d'attaque). Lignes bonus des épées, haches et marteaux : plus de points de vie, de résistance au
  recul ni de réduction de dégâts.
- **Plus de compétence sur les objets d'exemple** (épée, dague, bâton, Lame du Veilleur) : les compétences d'armes
  de Babel viendront avec la liste d'Antoine. Le système (`competences.yml`) reste en place.
- **Cimeterre de cuivre** (`cuivre/lame_large`) : la courbe part maintenant vers l'arrière une fois en main (dos à
  droite, tranchant à gauche dans le dessin).
- Sur un serveur déjà lancé, les exemples ne sont pas réinstallés : supprimer `materiaux/exemples.yml`,
  `objets/exemples.yml` et `ressources/modeles/exemple`, `ressources/textures/exemple` pour les recevoir.

## 0.10.2 (novembre 2026) : pièces muettes, qualité cachée par pièce

- **Rien n'est écrit sur une pièce**, à part son nom, lame comprise : ni stats, ni qualité, ni fabricant. On ne peut
  pas trier ses pièces et jeter les moins bonnes : il faut finir l'arme pour savoir ce qu'elle vaut.
- **La qualité revient sur chaque pièce, mais cachée** : `VoraceItemAPI.creerPiece(pièce, score)` garde le score
  dans l'objet, et `VoraceItemAPI.assemblerPieces(kit, pièces, ...)` donne à l'arme la moyenne des scores de ses
  pièces (comme en 0.10.0). `/vitem piece <joueur> <pièce> [score]`, `/vitem monter <joueur> [kit]`.
- Une pièce ne s'empile pas.
- **Exemples** : les stats des pièces reviennent à celles de la 0.10.0 (le déplacement de l'attaque sur les lames,
  fait en 0.10.1, est annulé).

## 0.10.1 (novembre 2026) : des pièces sans qualité, des stats sur la lame seulement

- **Une pièce ne porte plus que son nom**, et ses stats quand elle en a. Plus de ligne de qualité, de type ni de
  fabricant sur une pièce.
- **Plus de qualité par pièce** : la qualité se décide à la fin, à l'assemblage. `VoraceItemAPI.creerPiece(pièce)` ne
  prend plus de score, `VoraceItemAPI.piece(objet)` renvoie l'identifiant de la pièce, et
  `VoraceItemAPI.assemblerPieces(kit, pièces, score, ...)` reçoit le score final. `/vitem piece <joueur> <pièce>
  [quantité]`, `/vitem monter <joueur> [kit] [score]`.
- **Exemples** : seule la lame porte des stats. Les gardes, poignées et pommeaux n'ont plus qu'un nom et une
  apparence (ils comptent encore pour la durabilité de l'arme) ; l'attaque qu'ils apportaient est passée sur les
  lames (+4). Une pièce peut toujours recevoir des stats dans `materiaux/*.yml`.
- Deux pièces identiques s'empilent.

## 0.10.0 (novembre 2026) : les pièces d'armes sont des objets

- **Une pièce peut exister seule** dans l'inventaire (une lame, une garde, une poignée, un pommeau), avec son nom
  (« Lame droite de fer »), ses stats, son fabricant et son modèle 3D recentré dans sa case.
- **Chaque pièce a sa propre qualité** (un score de 0 à 100). L'arme montée prend la moyenne des scores de ses pièces,
  qui donne son grade, son rang et sa place dans la fourchette. Une arme montée ne se démonte pas.
- **API** pour le plugin des métiers : `VoraceItemAPI.creerPiece`, `piece`, `coutPiece`, `assemblerPieces`.
  vorace-item ne décide pas comment une pièce se fabrique.
- **Commandes d'essai** : `/vitem piece <joueur> <pièce> [score]` et `/vitem monter <joueur> [kit]`.
- Le pack contient un objet par pièce (`items/piece/...`). La forge au bouton (`/vitem forge`) ne change pas : elle
  fabrique toujours l'arme entière d'un coup.

## 0.9.3 (novembre 2026) : lames de cuivre

- Les deux lames de cuivre sont plus longues (elles étaient trop courtes par rapport au manche).
- La lame large de cuivre devient un cimeterre : dos à gauche, lame courbe qui s'élargit avant la pointe.
- La lame en feuille a une silhouette plus régulière.

## 0.9.2 (novembre 2026) : pièces d'exemple dans le style de Minecraft

Les pièces de la 0.9.1 ressemblaient à des épées de jeu 2D (lames larges, contours noirs, reflets en biais, grosses
gemmes, couleurs vives). Elles sont redessinées dans le style des objets de Minecraft :

- **peu de couleurs** : 5 tons par matière, proches de ceux des lingots, du bâton et du cuir du jeu ;
- **aplats en deux bandes** (moitié claire, moitié sombre) avec quelques taches d'usure, sans contour noir ;
- **lames étroites** (5 à 11 pixels), gardes fines de 3 pixels, manche simple à ligatures, petit pommeau ;
- trois ensembles toujours distincts et mélangeables : épée courte de cuivre qui verdit, épée d'armes de fer, épée
  longue d'acier sombre à garde d'or.

La technique (dessin de 128 pixels découpé en cubes d'épaisseur variable) et les identifiants des pièces ne changent
pas. Pour les installer sur un serveur déjà lancé, voir la 0.9.1.

## 0.9.1 (novembre 2026) : pièces d'exemple redessinées

- **Trois ensembles de pièces d'épée**, un par matériau, chacun avec son style (avant : les mêmes formes recolorées) :
  - cuivre : bronze de légionnaire (lame en feuille, fauchon, cuir clair, pommeau en anneau, vert-de-gris) ;
  - fer : épée de chevalier (lames à gouttière, gardes dorées, cuir rouge au fil d'or, pommeau à rubis) ;
  - acier : lame royale d'acier bleu (arête incrustée, espadon à ergots, gardes d'argent à saphir, pommeau de cristal).
  Les pièces gardent leurs identifiants (`fer/lame_droite`...) et se mélangent toujours entre ensembles.
- **Nouvelle technique de dessin** (`outils/exemples-pieces.py`) : chaque pièce est un dessin de 128 pixels découpé en
  petits cubes, avec une épaisseur par zone (tranchant très fin, plat de lame fin, garde et pommeau épais). Une épée
  compte 100 à 250 cubes au lieu de 16.
- Réglages d'affichage de l'épée d'exemple refaits pour ces pièces (`objets/exemples.yml`, `modulaire.affichage`).
  Ils restent à ajuster en jeu avec un vrai client.
- Sur un serveur déjà lancé, les exemples ne sont pas réinstallés : pour avoir les nouvelles pièces, supprime
  `plugins/VoraceItem/ressources/modeles/exemple`, `plugins/VoraceItem/ressources/textures/exemple` et copie le bloc
  `affichage` du nouveau `objets/exemples.yml`, ou repars d'un dossier `plugins/VoraceItem` vide.

## 0.9.0 (novembre 2026) : premier vorace-item complet

La 0.1.0 n'était qu'un squelette (le plugin démarrait et vérifiait que vorace-core était là). La 0.9.0 apporte toute
la feuille de route. Elle demande **Paper 1.21.4 ou plus** (au lieu de 1.21.1) et vorace-core 1.2.1 ou plus : les
armes assemblées dans le pack et le temps de recharge visible sur les objets n'existent pas avant.

### Stats, rareté, exemplaires

- **7 rangs de rareté** (`raretes.yml`) : Commun, Peu commun, Rare, Épique, Légendaire, Mythique, Divin. Couleur du
  nom, lignes bonus et emplacements de gemmes par rang.
- **Stats d'objet et modificateurs** (`modificateurs.yml`) : Attaque physique, Puissance magique, Armure physique,
  Armure magique, et les 38 modificateurs de Babel en 9 familles. Les valeurs sont en flat, sauf les chances,
  conversions et vitesses (points de %).
- **Types d'objets** (`types.yml`) : épée, dague, hache, marteau, lance, arc, bâton, baguette, bouclier, casque,
  plastron, jambières, bottes, outil, gemme, pierre d'amélioration, matériau.
- **Modèles d'objets** en YAML (`objets/*.yml`) : type, rang de base, stats, modificateurs fixes, lignes bonus,
  scales, élément, durabilité, conditions de niveau et de classe, compétences, gemmes, recette.
- **Fourchettes de stats** : une par rang, contiguës (le haut d'un rang est le bas du suivant), plus la fourchette
  « rebut » sous le Commun. Un modèle n'existe qu'à son rang de base, au rang du dessous ou au rang du dessus.
- **Données par exemplaire**, gardées dans l'objet : rang, place dans la fourchette, grade et score de fabrication,
  identifiant unique, fabricant, pièces, lignes bonus, gemmes, amélioration, durabilité, usure de combat.
  vorace-core (1.2.1) les conserve quand il met un objet à jour.
- **Mise en page** : nom à la couleur du rang, puis rang et type, qualité, stats, scale, modificateurs, gemmes,
  compétences, conditions, texte, durabilité, fabricant.
- **Qualité de fabrication** (règles du cahier des métiers de Babel) : grades Brute, Correcte, Fine, Excellente,
  Chef-d'œuvre ; score exigé pour les rangs hauts ; +1 rang possible sur un Chef-d'œuvre ; place dans la fourchette
  continue, avec un petit aléa.

### Armes modulaires

- Chaque **matériau** (`materiaux/*.yml`) a ses **pièces** (lame, garde, poignée, pommeau...), combinables entre
  elles. Les stats de l'arme sont la somme de celles de ses pièces, avec le palier de chaque matériau.
- **Un seul objet dans le pack** par type d'arme : un modèle « composite » de Minecraft 1.21.4 choisit une pièce par
  emplacement. vorace-item écrit sa partie du pack dans `plugins/Vorace/pack/sources/voraceitem` et vorace-core la
  fusionne (aucun changement dans vorace-core).
- Exemples fournis : une épée en cuivre, fer et acier (6 lames, 6 gardes, 3 poignées, 3 pommeaux : 324 épées),
  dessinée par `outils/exemples-pieces.py`.

### Combat

- **Formule de dégâts de Babel** : Puissance² ÷ (Puissance + Défense), scale sur deux stats (lettres S à E),
  critique × 1,25 sur les coups physiques seulement, esquive plafonnée à 20 %, dégâts bruts, pénétration, dégâts
  élémentaires, vol de vie et de mana, renvoi de dégâts, réduction des dégâts.
- Coups de mêlée, tirs à l'arc et compétences des joueurs ; coups des monstres contre les joueurs.
- Un objet ne compte que porté à sa place, non cassé, conditions remplies. Seuls les objets de type arme comptent
  comme arme en main (pas un outil).
- Par défaut, les coups portés sans arme de vorace-item et les coups reçus par un joueur sans objet de vorace-item
  gardent les règles du jeu de base ; `combat.formule-partout: true` applique la formule à tous les coups (Babel).
- Modificateurs appliqués par les attributs du jeu : vie maximale, vitesses de déplacement et d'attaque, résistance
  au recul, chance.
- **Stats des joueurs** : fournies par le plugin des classes (`ProfilJoueur`), sinon un profil par défaut réglable
  avec `/vitem profil`. **Mana et stamina** : fournis par le plugin des classes (`RessourcesJoueur`), sinon des
  jauges tenues par vorace-item.

### Durabilité, atelier, forge

- **Durabilité** propre à vorace-item : un objet usé est cassé, pas détruit ; −40 % de durabilité restante à la mort.
- **Atelier** (`/vitem atelier`) : réparer (0,5 % d'usure permanente par réparation, plus de réparation sous 50 % de
  la durabilité d'origine), améliorer avec une pierre (+3 % de stats de rang par niveau, chances réglables), sertir
  une gemme.
- **Forge** (`/vitem forge`) : liste des modèles à recette et des armes modulaires, choix des pièces une à une,
  aperçu, matériaux demandés. Résultat d'un débutant, jamais au-dessus du rang Rare (réglable).
- L'enclume, la meule, la table d'enchantement et l'établi du jeu de base ne touchent pas aux objets de vorace-item.

### Compétences d'armes, butin

- **Compétences** (`competences.yml`) : un clic lance la compétence ; coût en mana ou en stamina ; temps de recharge
  affiché sur l'objet. Effets : dégâts (visée, zone, cône), projectile, ruée, saut, soin, effet de potion, son,
  particules, message, commande.
- **Butin** (`butins.yml`) : tables par étage, armes assemblées avec les pièces des matériaux de l'étage (90 %
  Commune, 10 % Peu commune), modèles précis, objets simples. Les mobs de vorace-mob sont reconnus par leur
  identifiant, sans dépendance à vorace-mob.

### Commandes, API, essais

- **`/vitem`** : forge, atelier, donner, assembler, catalogue, info, reparer, nommer, profil, butin, competence, pack,
  recharger, essai.
- **API** (`fr.vorace.item.api.VoraceItemAPI`) : créer un exemplaire, assembler une arme, lire les stats et les
  fourchettes, calculer la qualité à partir d'un score, ajouter une ligne bonus, réparer, frapper avec la formule,
  lancer une compétence, tirer un butin. Événements `VoraceItemDegatsEvent`, `VoraceItemCompetenceEvent`, `VoraceItemCreeEvent`,
  `VoraceItemRepareEvent`.
- **Tests sans serveur** (tâche Gradle `tests`) : rangs et fourchettes, qualité, formule de dégâts (exemples du
  document de Babel), fichiers d'exemple, stats d'une arme modulaire, mise en page, pack.
- **Essai sur un vrai serveur** (GitHub Actions, Paper 1.21.4) : un faux joueur reçoit une épée modulaire, tue un
  cochon d'un coup, ouvre la forge et forge une épée ; `/vitem essai` vérifie les objets, le pack, le combat et les
  compétences.

### Ce qui n'est pas dans vorace-item

- Les **gestes de fabrication** (mini-jeux, maîtrise, métiers) : c'est le plugin des métiers, qui utilisera l'API.
- Les **altérations d'état** et les **compétences de classe** : c'est le plugin des classes
  (`VoraceItemDegatsEvent` donne le type d'attaque et l'élément de chaque coup).
- Les modificateurs sans effet dans vorace-item (dégâts de zone, dégâts sur la durée, ténacité, expérience...) sont
  additionnés sur l'équipement et lisibles par `VoraceItemAPI.total`.

### À vérifier avec un vrai joueur

- La tenue des armes modulaires en main et dans l'inventaire (réglages `affichage` du kit d'exemple) : ils sont
  calculés, pas encore regardés en jeu.
- L'équilibrage des chiffres d'exemple.

## 0.1.0 (novembre 2026)

Squelette : le plugin démarre et vérifie que vorace-core est là.

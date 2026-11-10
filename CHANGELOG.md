# Journal des changements

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

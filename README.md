# vorace-item

Des **objets RPG** pour serveurs Paper : stats, rareté, armes modulaires, forge et métiers. Fait partie de
[VoracePlugin](https://github.com/VoracePlugin) et s'appuie sur [vorace-core](https://github.com/VoracePlugin/vorace-core)
(plugin `Vorace`), qui gère déjà l'apparence des objets, le pack de ressources, les recettes et les actions au clic.

> **En préparation.** Le plugin démarre et vérifie que vorace-core est là ; les fonctions ci-dessous sont le plan.
>
> **Code écrit par une IA.** Comme vorace-core, vorace-item est écrit par Claude (l'IA d'Anthropic) ; ORA et Antoine
> testent et décident de ce qui est ajouté.

## Ce que fera vorace-item

1. **Stats RPG** sur les objets : dégâts, critique, vitesse, élément, défense ; conditions de classe ou de niveau.
2. **Rareté fixe** par objet (une arme a un seul rang et son propre modèle) : couleur du nom, mise en page de la
   description.
3. **Armes modulaires** : chaque matériau a ses pièces (lame, garde, poignée, pommeau), combinables entre elles. Le
   pack les assemble en un seul objet (Minecraft 1.21.4+), donc toutes les combinaisons existent sans être dessinées
   une par une. Les armes légendaires et divines restent des modèles complets faits à la main.
4. **Données propres à chaque exemplaire** : stats tirées au hasard, qualité, nom du forgeron.
5. **Forge et métiers** : fabrication dans un menu, recettes en plusieurs étapes, qualité selon le niveau du métier,
   réparation, amélioration, gemmes à sertir.
6. **Compétences d'armes** : clic qui lance une compétence, temps de recharge visible sur l'objet, mana.
7. **Butin** : avec [vorace-mob](https://github.com/VoracePlugin/vorace-mob), les monstres lâchent des armes de
   base assemblées avec les pièces des matériaux de leur étage.

## Ce qu'il faut ajouter à vorace-core pour ça

- Assemblage des pièces dans le pack (modèle composé, une pièce choisie par objet).
- Mise à jour des objets à la connexion qui garde les données de chaque exemplaire.
- Points d'accroche dans l'API : objet utilisé, coup porté (`VoraceObjetUtiliseEvent` existe déjà pour les clics),
  temps de recharge visible.

## Installation (plus tard)

Mets `Vorace-x.y.z.jar` ([vorace-core](https://github.com/VoracePlugin/vorace-core)) et `VoraceItem-x.y.z.jar` dans `plugins/`.

## Compiler

```
gradle build                                                   # vorace-core pris sur JitPack
gradle build -PvoraceCore=../vorace-core/build/libs/Vorace-1.2.0.jar
```

## Licence

MIT.

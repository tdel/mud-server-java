# État des lieux et feuille de route des systèmes DnD5e

## Contexte

Le projet est un MUD Java/Spring Boot qui vise à reprendre les règles DnD5e. Cet état des lieux liste ce qui existe déjà dans le code, puis ce qui manque ou reste superficiel, organisé par système de jeu avec un ordre de dépendance suggéré. C'est une référence, pas un plan d'implémentation détaillé d'une fonctionnalité précise.

## Ce qui existe déjà (pour référence, ne pas refaire)

- **Comptes/session** : login/register/logout/quit, BCrypt, un seul virtual thread par connexion avec queue ordonnée.
- **Personnage** : création avec génération 4d6-drop-lowest (`CharacterCreate.java`), 4 races (`Race.java`) avec bonus de caractéristiques DnD5e corrects, `Attribute` (les 6 caractéristiques, sans modificateur calculé).
- **Monde** : `Room`/`RoomExit` en cache mémoire chaud, navigation (`go`), description (`look`), broadcast d'arrivée/départ.
- **Objets** : `ItemTemplate`/`Item`, 10 types, 6 slots d'équipement, take/drop/equip/unequip avec gestion de concurrence (`synchronized(item)`, événements de domaine + projection DB).
- **Social minimal** : `say` (chat de salle), `stats`/`examine` (affichage HP + caractéristiques brutes, sans modificateurs).
- **Utilitaire** : lanceur de dés générique `roll XdY+Z` (`game/dice/`), réutilisé pour la génération de personnage.
- **Modèle de concurrence** : aucun `@Scheduled` nulle part — la boucle de jeu est 100% réactive aux commandes joueur, pas de tick de fond.

`currentHealth`/`maxHealth` existent en base et en Java mais sont figés à 100/100 à la création et ne sont **jamais** modifiés ni lus ailleurs que l'affichage — donc aucun dégât, aucune mort n'est possible aujourd'hui.

## Systèmes absents ou à l'état de simple champ de données

### 1. Fondations de personnage (bloquant pour presque tout le reste)
- ~~**Modificateurs de caractéristiques** : `(score-10)/2` n'existe nulle part, même pas comme méthode utilitaire sur `Attribute`/`Character`. Tout système ultérieur (combat, compétences, jets de sauvegarde) en dépend.~~
- ~~**Bonus de maîtrise (proficiency bonus)** : absent, dépend du niveau.~~
- ~~**Classe de personnage** (Guerrier, Magicien, etc.) : totalement absente — pas de table, pas d'enum, pas de champ. Impacte points de vie par niveau, jets de sauvegarde maîtrisés, compétences, accès aux sorts.~~
- **Niveau / XP** : absents. Aucun champ, aucune table.
- Ordre suggéré : modificateurs de caractéristiques → bonus de maîtrise/niveau → classe → XP.

### 2. Combat
Rien n'existe : pas de verbe `attack`/`fight`, pas de `CombatService`, pas de message de sortie (`Attacked`/`Damaged`/`Died`), pas de résolution de touche, pas de Classe d'Armure, pas d'initiative/ordre de tour. C'est le plus gros chantier vide du projet.
- **Classe d'Armure (CA)** : `ItemType.ARMOR` existe comme catégorie de slot mais ne contribue à aucune valeur numérique.
- **Jet d'attaque / dégâts** : le lanceur de dés générique existe déjà et peut être réutilisé tel quel pour les jets de dégâts d'arme, mais aucun objet (`Item`) ne porte de dé de dégâts.
- **Initiative / ordre de tour** : absent.
- **Mort / réapparition** : rien ne teste `currentHealth <= 0`.
- Dépend de : modificateurs de caractéristiques, bonus de maîtrise, et d'une cible à combattre (§4 PNJ/monstres) pour être testable en jeu.

### 3. Jets de sauvegarde et compétences
Absents. Nécessitent modificateurs de caractéristiques + bonus de maîtrise + (pour les compétences maîtrisées) la classe de personnage.

### 4. PNJ / Monstres / IA
Rien n'existe : `Room.clients` ne contient que des `Character` joueurs, aucun type de domaine monstre, aucune table de spawn, aucune IA d'agressivité. Prérequis direct pour rendre le combat jouable en solo/PvE (le MUD n'a aujourd'hui aucun adversaire possible hors PvP, qui lui-même n'existe pas non plus).

### 5. Sorts / lancer de sorts
Totalement absent (pas de table, pas de slot de sort, pas de composant verbal/somatique/matériel). Dépend de la classe de personnage.

### 6. États (conditions)
Absents (empoisonné, étourdi, paralysé, charmé, effrayé...). Dépend d'avoir un système de combat pour être déclenché.

### 7. Repos (courte/longue pause)
Absent. Dépend d'avoir des ressources à restaurer (HP, emplacements de sorts) pour avoir un sens.

### 8. Économie
- **Monnaie** : totalement absente (pas de or/argent/cuivre).
- **Encombrement** : `item_template.weight` est stocké et lisible (`ItemTemplate.weight`, `Item.getWeight()`) mais rien ne fait la somme ni ne la compare à la Force — champ mort aujourd'hui.
- **Boutiques/marchands** : absents.

### 9. Contenu de monde
Quêtes, dialogues PNJ, factions, alignement : tous absents. Dépendent des PNJ (§4).

### 10. Commandes sociales/admin manquantes
`who`, `tell`/`whisper`, `emote`, `help`, `give` (transfert d'objet entre joueurs), commandes de modération/wizard : aucune n'existe. Indépendantes du reste, faisables à tout moment via `/add-command`.

## Lacune transverse notée en passant

Aucun test au niveau `controller/**` (Login, CharacterCreate, Go, Take, Equip, Roll, etc.) — seule la couche `game`/`persistence` est testée. Ce n'est pas un "système DnD5e" mais ça vaut d'être su avant d'empiler des commandes de combat par-dessus une couche non testée.

## Ordre de construction suggéré

1. Modificateurs de caractéristiques + bonus de maîtrise (petit, débloque tout le reste)
2. Classe de personnage + Niveau/XP (fondation de progression)
3. PNJ/monstres basiques (cible statique, sans IA) dans `Room`
4. Combat minimal : CA, jet d'attaque, dégâts, mort (contre les PNJ de l'étape 3)
5. Jets de sauvegarde et compétences (réutilisent les modificateurs de l'étape 1)
6. États, repos (s'appuient sur le combat)
7. Sorts (s'appuie sur classe + combat + états)
8. Monnaie, encombrement (utilise `weight` déjà stocké), boutiques
9. Quêtes, dialogues PNJ, factions, alignement (contenu, dépend de tout le reste)
10. Commandes sociales/admin (`who`, `tell`, `emote`, `help`) — indépendant, à intercaler n'importe quand

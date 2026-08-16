# Stopper le mouvement à l'entrée en zone d'aggro d'un monstre

## Contexte

Le déplacement a été récemment refondu en case-par-case via `MovementTicker` (commit `d51e4b6`). À cette occasion, le hook `onEnteredCell(HexCoordinate)` — déjà présent et déjà câblé côté combat (`GamePlayer.onEnteredCell` publie `GamePlayerEnteredCell`, `CombatEngine.onGamePlayerEnteredCell` déclenche l'aggro via `presenceRadius`) — a cessé d'être appelé. Vérifié exhaustivement (grep + graft) : **aucun appelant** de `onEnteredCell` n'existe plus dans `src/main`, ni dans `moveOneCell` ni dans `MovementTicker`.

Conséquence : **aujourd'hui, se déplacer case par case ne déclenche plus jamais l'aggro**, malgré CLAUDE.md qui documente ce comportement comme existant ("Monsters have a presence/aggro zone that triggers combat when a player enters it"). C'est une régression du refactor `MovementTicker`, pas une fonctionnalité à concevoir de zéro : tout le câblage aval (event, listener, rayon de présence) est prêt et fonctionnel — seul l'appel manque, et rien n'arrête le mouvement en cours une fois le combat démarré.

Ce plan présente plusieurs options d'implémentation pour ce correctif, comparées, avec une recommandation.

## Le piège commun à toutes les options : la résurrection de `activeMovement`

`GameCharacter.updatePosition(long now)` capture `ActiveMovement movement = this.activeMovement` **avant** d'appeler `moveOneCell`. Si le combat démarre de façon synchrone à l'intérieur de cet appel (aggro via `onEnteredCell` → `GamePlayerEnteredCell` → `CombatEngine`, listener non-`@Async`), et que `activeMovement` est mis à `null` à ce moment-là, la suite non gardée de `updatePosition` (`this.activeMovement = movement.withRemaining(...)`) écraserait ce `null` avec l'ancien état — le mouvement "ressusciterait" malgré le combat. Le ticker étant mono-thread (`MovementTicker extends Thread`, boucle séquentielle) et les listeners synchrones, ce n'est pas une race multi-thread classique mais un problème de réentrance/contrôle de flux — le fix est structurel, pas une synchronisation.

**Toute option retenue doit donc inclure un garde explicite dans `updatePosition`** qui vérifie `isInCombat()` après le pas et court-circuite avant de réarmer `activeMovement`.

## Options comparées

### Option A — Domaine (`GameCharacter.moveOneCell` + `updatePosition`)
Appeler `onEnteredCell(next)` dans `moveOneCell` juste après `setPosition(next)`. Dans `updatePosition`, après un pas réussi, garde explicite avant le réarmement :
```java
if (isInCombat()) {
    this.activeMovement = null;
    return MovementStepOutcome.STOPPED_BY_COMBAT; // nouvelle valeur d'enum
}
```
- **+** Logique métier localisée dans le domaine (conforme à la convention CLAUDE.md : objets métier auto-suffisants). Diff minimal, anti-résurrection local et explicite.
- **−** Ne couvre que l'aggro. Ne traite pas le cas connexe "attaque volontaire pendant un déplacement en cours" (voir plus bas).

### Option B — Ticker (`MovementTicker.processIfDue`)
`AbstractCharacter` inchangé. Après `STEPPED`/`FINISHED`, le ticker appelle lui-même `character.onEnteredCell(character.getPosition())`, puis `stopMovement()` si `isInCombat()`.
- **+** Domaine ignorant du combat déclenché par le déplacement.
- **−** Déplace un invariant métier vers la couche d'orchestration (moins conforme à CLAUDE.md, qui réserve les listeners de Services à la persistance, pas à la réimplémentation d'invariants). Aller-retour d'event superflu. Même lacune que A sur le cas "attaque volontaire".

### Option C — Centralisé sur `setEncounter` (complément, pas une alternative à A)
```java
public void setEncounter(CombatEncounter encounter) {
    if (encounter != null) {
        stopMovement();
    }
    this.encounter = encounter;
}
```
Validé via `graft callers setEncounter` : **7 appelants, tous dans `CombatEngine`**, aucun test, aucun effet de bord caché — hook sûr.
- **+** Couvre *toute* entrée en combat, y compris le cas non couvert par A/B : un joueur en déplacement multi-cases qui tape volontairement `attack` sur un monstre de la même room (chemin qui ne passe jamais par `onEnteredCell`). Réutilise `stopMovement()` existant et idempotent (pas de double event même combiné avec le garde de `updatePosition`).
- **−** Seule, ne suffit pas : ne règle pas la résurrection côté `updatePosition` pour le cas aggro (voir piège ci-dessus) — c'est un complément à A, pas un remplacement.

## Recommandation : A + C (B écarté)

- **A** pour le câblage `onEnteredCell` et le garde anti-résurrection dans `updatePosition` — seul mécanisme qui empêche réellement la résurrection.
- **C** pour fermer gratuitement le cas "attaque volontaire pendant déplacement" et pour toute robustesse future (nouvelle source de combat = couverte automatiquement), en réutilisant `stopMovement()` déjà existant (utilisé par la commande `Stop`).
- **B écarté** : duplique un invariant hors du domaine sans avantage sur A+C, et couvre moins de cas.

### Fichiers à toucher

1. **`src/main/java/fr/idev/mudserver/domain/actor/GameCharacter.java`**
   - `moveOneCell(HexDirection)` : ajouter `onEnteredCell(next);` après `setPosition(next);`.
   - `updatePosition(long now)` : garde `if (isInCombat()) { this.activeMovement = null; return MovementStepOutcome.STOPPED_BY_COMBAT; }` avant le réarmement de `activeMovement`.
   - `enum MovementStepOutcome` : ajouter `STOPPED_BY_COMBAT`.
   - `setEncounter(CombatEncounter)` : ajouter l'appel à `stopMovement()` si `encounter != null`.
   - Nettoyage : supprimer le record mort `MovementOutcome` (L206-208, zéro appelant confirmé) — vestige d'un design antérieur qui anticipait déjà ce besoin (champ `triggeredCombat` jamais branché).

2. **`src/main/java/fr/idev/mudserver/game/MovementTicker.java`**
   - `processIfDue` : `case STOPPED_BY_COMBAT -> movingCharacters.remove(character.getId());` — **sans** notification générique (`CombatEngine` envoie déjà `MonsterAggroTriggered`/`MonsterAggroBroadcast`, un message de blocage en plus serait un doublon confus).

3. Aucun changement requis : `GamePlayer.onEnteredCell`, `CombatEngine` (câblage déjà correct, simplement enfin invoqué), `HexGridRenderer`/`remainingPath()` (se corrige automatiquement dès que `activeMovement` repasse à `null`), `MonsterInstance`/`AbstractNpc` (héritent d'un `onEnteredCell` no-op, inoffensif — confirmé qu'aucun monstre/PNJ n'utilise `activeMovement` aujourd'hui, `startMovement` n'étant appelé que par `Go.onReceive`).

### Renommage `presenceRadius` → `aggroRadius`

Confirmé avec l'utilisateur : à inclure. Pur renommage (aucun impact sur la conformité SRD DnD5e, terme non utilisé dans `docs/dnd5e/`). Fichiers concernés :
- `src/main/java/fr/idev/mudserver/domain/actor/MonsterTemplate.java` (champ + accesseurs `getPresenceRadius`/`setPresenceRadius`)
- `src/main/java/fr/idev/mudserver/domain/actor/GameMonster.java` (`getPresenceRadius()` délégué)
- `src/main/java/fr/idev/mudserver/game/actor/MonsterService.java` (record `MonsterTemplateDefinition`, mapping JSON)
- `src/main/java/fr/idev/mudserver/game/CombatEngine.java:314` (`monster.getPresenceRadius()`)
- `src/main/resources/data/monsters.json` (5 occurrences de la clé `"presenceRadius"`)

## Risques / comportements adjacents vérifiés

- **GameMonster/GameNpc** : non impactés (pas d'usage de `activeMovement` aujourd'hui).
- **`ControllerDispatcher`** bloque déjà `go` pendant le combat — orthogonal, inchangé.
- **`stopMovement()` idempotent** : pas de double publication de `CharacterStoppedMoving` même si C et le garde de `updatePosition` se déclenchent dans le même enchaînement.
- **Fin de combat** (`setEncounter(null)`) : le hook C ne se déclenche que sur `encounter != null`, donc la fin de combat n'appelle jamais `stopMovement()` par erreur.
- Cas limite pré-existant hors scope : fenêtre de concurrence entre un `stop` manuel et le traitement du tick — déjà présente aujourd'hui, non liée à ce correctif.

## Vérification

`src/test` est actuellement **vide** (premier test du projet) mais `spring-boot-starter-test`/`testcontainers-junit-jupiter` sont déjà en dépendance — aucun outillage à ajouter.

1. **Test unitaire domaine** (`AbstractCharacter`/`updatePosition`, sans contexte Spring) : simuler un déclenchement de combat pendant `onEnteredCell` et vérifier que `STOPPED_BY_COMBAT` est retourné, que `activeMovement` reste `null` (pas de résurrection), et qu'aucun pas supplémentaire n'a lieu au tick suivant.
2. **Test bout-en-bout aggro** (`CombatEngine`) : joueur en déplacement multi-cases entrant dans le rayon d'aggro d'un monstre — vérifier l'arrêt à la bonne case, le démarrage du combat, l'absence de message de blocage générique en double.
3. **Edge case attaque volontaire** : déplacement multi-cases en cours puis `CombatEngine.attack(...)` direct — vérifier l'arrêt net du mouvement (grâce à C).
4. **`MovementTicker.processIfDue`** (package-private, testable directement) : `STOPPED_BY_COMBAT` retire bien le personnage de `movingCharacters` sans notification.
5. Exécution : `docker run --rm -v "$(pwd)":/app -w /app -v /var/run/docker.sock:/var/run/docker.sock -v ~/.m2:/root/.m2 maven:3.9.16-eclipse-temurin-25 mvn test` (voir CLAUDE.md).
6. Vérification manuelle optionnelle via le skill `run` : marcher case par case vers un monstre à rayon d'aggro connu, observer l'arrêt exact et l'affichage `HexGridRenderer` (plus de destination/chemin affiché une fois le combat commencé).

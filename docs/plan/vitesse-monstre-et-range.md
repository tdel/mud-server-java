# Portée d'attaque et déplacement en combat

## Contexte

Le combat (`CombatEngine`) résout aujourd'hui toute attaque sans jamais vérifier la distance hexagonale entre l'attaquant et sa cible, alors que joueurs et monstres occupent déjà des cases distinctes (`RoomInstance.occupants`) et que `HexCoordinate.distanceTo` est déjà utilisé pour l'aggro (`presenceRadius`). Les monstres n'ont par ailleurs aucune IA de déplacement — ils sont statiques après spawn. Le but est d'introduire une vraie notion de portée d'arme/attaque : une attaque hors de portée est refusée avec un message clair, le joueur doit se rapprocher (commande `go`, actuellement bloquée en combat), et le monstre doit pouvoir avancer vers sa cible pendant son tour.

Confirmé en lisant le code (pas seulement supposé) :
- `CommandDispatcher.COMBAT_ALLOWED_VERBS` (`network/CommandDispatcher.java:20`) ne contient pas `"go"` → tout mouvement est bloqué en combat aujourd'hui (`CombatActionRequired`).
- `speed` (JSON monstres/races) est déjà exprimé en **cases**, pas en pieds (`GameCharacter.REFERENCE_SPEED = 5`, `getMillisPerCell() = 1000 * 5 / speed` — humain 30ft/5=6, nain 25ft/5=5, cohérent avec les JSON existants).
- `GameCharacter.moveOneCell(HexDirection)` (`domain/actor/GameCharacter.java:130-146`) gère déjà atomiquement bornes + `tryClaimCell`/`releaseCell` + `setPosition` — outil direct pour un déplacement synchrone du monstre.
- `src/test` est vide (nettoyage volontaire antérieur) → **pas de tests à ajouter** pour cette fonctionnalité (confirmé avec l'utilisateur).
- Le déplacement du joueur en combat sera **plafonné à sa vitesse** par tour (confirmé avec l'utilisateur), cohérent avec le budget mouvement du monstre.

## Étape 1 — Portée des armes (`ItemTemplate`)

- `domain/ItemTemplate.java` : ajouter `private int range;` (dernier paramètre du constructeur, après `bonus`), `getRange()`/`setRange()`, l'inclure dans `equals`/`hashCode`/`toString`.
- `domain/ConsumableItem.java` (constructeur L18) et `domain/FoodItem.java` (constructeur L12) : répercuter le nouveau paramètre `range` dans leur appel `super(...)`.
- `domain/Item.java` : ajouter `public int getRange() { return template.getRange(); }`, même pattern que `getBaseAc()`/`getDamageDice()` (L51-57).
- `game/ItemTemplateService.java` : `ItemTemplateDefinition` record (L87-89) ajoute `int range` ; les branches de `toTemplate` (L56-…) passent `definition.range()` à chaque constructeur (`ItemTemplate`, `ConsumableItem`, `FoodItem`).
- `src/main/resources/data/items.json` : ajouter `"range": 1` aux entrées de type `WEAPON` (armes de mêlée actuelles — aucune arme à distance n'existe encore).

## Étape 2 — Portée effective du joueur (`CharacterInstance`)

- `domain/actor/GamePlayer.java` : ajouter `getAttackRange()`, réutilisant le même lookup d'arme équipée que `tryAttack` (L216-218, `inventory.getEquippedItems().stream().filter(item -> item.getSlot() == EquipmentSlot.WEAPON).findFirst()`) :

```java
private static final int UNARMED_ATTACK_RANGE = 1;

public int getAttackRange() {
    return inventory.getEquippedItems().stream()
            .filter(item -> item.getSlot() == EquipmentSlot.WEAPON).findFirst()
            .map(Item::getRange)
            .orElse(UNARMED_ATTACK_RANGE);
}
```

## Étape 3 — Portée d'attaque des monstres (`MonsterTemplate`/`MonsterInstance`)

- `domain/actor/MonsterTemplate.java` : ajouter `private int attackRange;` au champ/constructeur (après `speed`), `getAttackRange()`/`setAttackRange()`, l'inclure dans `equals`/`hashCode`/`toString` — même structure que `presenceRadius`/`speed`.
- `domain/actor/GameMonster.java` : ajouter `public int getAttackRange() { return requireTemplate().getAttackRange(); }` — même pattern que `getPresenceRadius()`.
- `game/actor/MonsterService.java` : `MonsterTemplateDefinition` record (L105-107) + `registerTemplates` (appel `new MonsterTemplate(...)` L94-97) ajoutent `attackRange`.
- `src/main/resources/data/monsters.json` : ajouter `"attackRange": 1` à chacun des 7 monstres existants (attaques naturelles = corps-à-corps).

## Étape 4 — Nouveaux messages réseau

Sous `network/message/ingame/`, suivre le pattern des records existants (`TargetNotFound`, `MonsterAggroBroadcast`) :

- `TargetOutOfRange(String targetName, int distance, int range)` — envoyé à l'attaquant seul, message d'erreur (`Ansi.error(...)`) invitant à se rapprocher avec `go`.
- `MonsterApproachedBroadcast(String monsterName, int cellsMoved)` — diffusé à la room quand un monstre avance. Pas de domain event nécessaire : la position d'un monstre n'est jamais persistée en base (aucun DAO ne la référence).

## Étape 5 — Vérification de portée côté joueur (`CombatEngine`)

Deux points d'entrée d'attaque joueur à modifier :

- `startNewEncounter` (`game/CombatEngine.java:90-146`, le coup d'ouverture hors initiative) : vérifier la distance **avant** toute création d'`encounter` :
```java
int distance = attacker.getPosition().distanceTo(target.getPosition());
if (distance > attacker.getAttackRange()) {
    attacker.send(new TargetOutOfRange(target.getName(), distance, attacker.getAttackRange()));
    return;
}
```
- `performTurnAttack` (L182-198) : scinder la condition combinée existante pour ne pas consommer l'action sur un échec de portée — vérifier la distance **après** le contrôle de tour mais **avant** `trySpendAction()` :
```java
if (encounter.currentParticipant() != attacker) {
    attacker.send(new NotYourTurn());
    return;
}
int distance = attacker.getPosition().distanceTo(target.getPosition());
if (distance > attacker.getAttackRange()) {
    attacker.send(new TargetOutOfRange(target.getName(), distance, attacker.getAttackRange()));
    return;
}
if (!attacker.getActionEconomy().trySpendAction()) {
    attacker.send(new NotYourTurn());
    return;
}
```

`joinPlayerInto`/`mergeMonsterInto` n'ont pas besoin de garde : ils ne font que rejoindre l'ordre d'initiative, l'attaque effective passe ensuite par `performTurnAttack`.

## Étape 6 — Déplacement + attaque monstre (`CombatEngine.resolveFromCurrentTurn`)

Dans `resolveFromCurrentTurn` (L245-265), après le choix aléatoire de `victim` (inchangé) :

```java
int distance = monster.getPosition().distanceTo(victim.getPosition());
if (distance > monster.getAttackRange()) {
    moveMonsterToward(monster, victim);
    distance = monster.getPosition().distanceTo(victim.getPosition());
}
if (distance <= monster.getAttackRange()) {
    CombatResult result = monster.tryAttack(victim);
    victim.send(new MonsterAttackResult(monster.getName(), result));
    encounter.getRoom().broadcast(new MonsterAttackBroadcast(monster.getName(), result), victim);
    log.info(...);
    if (result.hit()) {
        victim.takeDamage(result.damage(), monster);
    }
} else {
    log.debug("combat.monster_out_of_range monster={} victim={} distance={}",
            monster.getName(), victim.getName(), distance);
}
encounter.advanceTurn();
```

Deux méthodes privées à ajouter à `CombatEngine` (imports à ajouter : `HexCoordinate`, `HexDirection`, `java.util.Arrays`, `java.util.Comparator`) :

```java
private void moveMonsterToward(GameMonster monster, GamePlayer target) {
    int cellsBudget = Math.max(0, monster.getSpeed()); // déjà en cases
    int cellsMoved = 0;
    while (cellsMoved < cellsBudget
            && monster.getPosition().distanceTo(target.getPosition()) > monster.getAttackRange()) {
        if (!tryStepToward(monster, target.getPosition())) {
            break; // bloqué : aucune case libre ne rapproche le monstre
        }
        cellsMoved++;
    }
    if (cellsMoved > 0) {
        monster.getCurrentRoom().broadcast(new MonsterApproachedBroadcast(monster.getName(), cellsMoved), null);
        log.info("combat.monster_approached monster={} target={} cellsMoved={}",
                monster.getName(), target.getName(), cellsMoved);
    }
}

private boolean tryStepToward(GameMonster monster, HexCoordinate targetPosition) {
    HexCoordinate from = monster.getPosition();
    int currentDistance = from.distanceTo(targetPosition);
    List<HexDirection> candidates = Arrays.stream(HexDirection.values())
            .filter(direction -> from.neighbor(direction).distanceTo(targetPosition) < currentDistance)
            .sorted(Comparator.comparingInt(direction -> from.neighbor(direction).distanceTo(targetPosition)))
            .toList();
    for (HexDirection direction : candidates) {
        if (monster.moveOneCell(direction).moved()) {
            return true;
        }
    }
    return false;
}
```

`moveOneCell` gère déjà bornes + occupation atomique (`RoomInstance.tryClaimCell`/`releaseCell`) — aucune duplication. `tryStepToward` teste les directions qui rapprochent réellement (distance strictement décroissante), triées par efficacité, et retombe sur la suivante si la case est occupée. Limite connue (MVP, pas de vrai pathfinding/contournement d'obstacle) — acceptable en salle ouverte.

## Étape 7 — Débloquer et plafonner le rapprochement du joueur

- `network/CommandDispatcher.java:20` : ajouter `"go"` à `COMBAT_ALLOWED_VERBS`.
- `network/command/ingame/Go.java` (`onReceive`, L33-56) :
  - Ajouter une garde de tour (symétrique à `performTurnAttack`), après récupération de `character` :
    ```java
    if (character.isInCombat() && character.getEncounter().currentParticipant() != character) {
        connection.send(new NotYourTurn());
        return;
    }
    ```
  - Plafonner le nombre de cases à la vitesse du joueur en combat (confirmé avec l'utilisateur), juste après le `Math.min(requestedCells, MAX_STEP_COUNT)` existant (L53) :
    ```java
    if (character.isInCombat()) {
        requestedCells = Math.min(requestedCells, character.getSpeed());
    }
    ```

Limite acceptée, pas dans le scope de cette demande : le mouvement du joueur reste asynchrone via `MovementTicker` et rien ne le suspend si son tour se termine avant la fin du trajet (ex. il attaque avant d'avoir fini de se déplacer) — gap préexistant dans `ActionEconomy`, pas introduit ici.

## Cas limites couverts

- **Monstre bloqué** : `tryStepToward` retourne `false` dès qu'aucune direction candidate n'obtient de case → le monstre avance partiellement (ou pas du tout), pas d'attaque ce tour, mais `advanceTurn()` s'exécute normalement (le combat n'est jamais bloqué).
- **Joueur qui fuit** : la distance est recalculée après le déplacement du monstre ; le monstre peut rester hors de portée si le joueur recule plus vite — comportement attendu.
- **Aggro** (`onGamePlayerEnteredCell`, inchangé) : utilise déjà `distanceTo` contre `presenceRadius`, un rayon distinct de la portée d'attaque. `startAggroEncounter` délègue à `resolveFromCurrentTurn`, qui bénéficie donc automatiquement de la nouvelle IA de déplacement — un monstre avec `presenceRadius=2` et `attackRange=1` avancera typiquement avant d'attaquer au premier tour après aggro.
- **`ActionEconomy`/ordre des tours** : aucune régression — `trySpendAction()` ne s'exécute qu'après validation de portée côté joueur ; côté monstre, `resetForTurn()`/`advanceTurn()` restent aux mêmes emplacements, seule l'attaque devient conditionnelle.
- **Point de vigilance connexe** (documenté dans `docs/plan/aggro-en-mouvement.md`, non traité par ce plan) : `onEnteredCell` n'est aujourd'hui appelé nulle part depuis `moveOneCell`, donc l'aggro pendant un déplacement case-par-case est déjà une régression préexistante, indépendante de cette fonctionnalité.

## Fichiers concernés

- `game/CombatEngine.java`
- `domain/actor/GamePlayer.java`, `domain/actor/GameMonster.java`
- `domain/ItemTemplate.java`, `domain/Item.java`, `domain/ConsumableItem.java`, `domain/FoodItem.java`
- `domain/actor/MonsterTemplate.java`
- `game/ItemTemplateService.java`, `game/actor/MonsterService.java`
- `network/CommandDispatcher.java`, `network/command/ingame/Go.java`
- Nouveaux : `network/message/ingame/TargetOutOfRange.java`, `network/message/ingame/MonsterApproachedBroadcast.java`
- `src/main/resources/data/items.json`, `src/main/resources/data/monsters.json`

## Vérification

Pas de tests unitaires ajoutés (décision confirmée). Vérification via build + lancement manuel :
1. `docker run --rm -v "$(pwd)":/app -w /app -v /var/run/docker.sock:/var/run/docker.sock -v ~/.m2:/root/.m2 maven:3.9.16-eclipse-temurin-25 mvn package` — compile, valide les JSON au warm-up (`ItemTemplateService`/`MonsterService` échouent au démarrage si un champ requis manque).
2. Lancer le serveur, se connecter en telnet, provoquer un combat, s'éloigner d'un monstre déjà engagé et tenter `attack` → vérifier le message `TargetOutOfRange`.
3. Utiliser `go` pendant son tour de combat pour se rapprocher, vérifier que le déplacement est plafonné à la vitesse et que `attack` réussit une fois à portée.
4. Déclencher l'aggro d'un monstre à distance (`presenceRadius` > `attackRange`) et observer le message `MonsterApproachedBroadcast` avant que le monstre n'attaque.

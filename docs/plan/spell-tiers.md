# Système de tiers de sorts (spell levels)

## Contexte

Actuellement chaque sort (`domain/Spell.java`) est une entrée figée dans `spells.json` : un `requiredLevel` (niveau perso pour l'apprendre), un `manaCost` et un `cooldownSeconds` fixes pour toute la partie. Un perso niveau 20 lance toujours "Fire Bolt" avec les mêmes 1d10 dégâts qu'au niveau 1. L'utilisateur veut que les sorts montent en puissance avec le niveau du perso : plus de dégâts/soin, mais en contrepartie plus de mana et un cooldown réduit à mesure qu'on progresse.

Architecture retenue (validée avec l'utilisateur) : **tiers explicites en catalogue**, pas de calcul dérivé à la volée. Chaque sort devient une famille de plusieurs entrées `Spell` (même `name`, `tier` croissant 1..N), chacune avec ses propres `requiredLevel`/`manaCost`/`cooldownSeconds`/`effectDice` écrits en dur dans `spells.json` — exactement le même principe que Fire Bolt (niv.1) / Scorching Ray (niv.3) / Fireball (niv.5) aujourd'hui, mais en gardant le même nom pour que ça se lise comme une progression d'un seul sort plutôt que 3 sorts distincts.

Cadence de tiers proposée pour l'écriture des données (guideline, pas imposée par le code) : +1 tier tous les 4 niveaux perso après le `requiredLevel` du tier 1, plafonné à 5 tiers (naturellement plus bas pour les sorts à `requiredLevel` élevé, le cap perso étant 20). Formule de scaling : dégâts/soin → +1 dé du même type par tier (modificateur fixe inchangé, ex. `3d4+3` → `4d4+3`) ; effets à valeur plate (ex. Shield of Faith) → +1 par tier ; mana → environ +25%/tier ; cooldown → environ -12,5%/tier, plancher 1s.

**Contrainte critique** : les UUID des tiers 1 existants (24 sorts) ne changent pas — seuls les nouveaux tiers ≥2 reçoivent des UUID neufs, pour ne pas casser les lignes `character_spell` déjà persistées.

## Changements par fichier

### 1. `domain/Spell.java`
Ajouter `int tier` juste après `name` (arité 12 → 13). Répercuter dans `SpellCatalog.SpellDefinition` (champ `Integer tier`, coalescé à `1` si absent, même pattern que `durationSeconds` existant) et dans les 3 seuls autres call-sites construisant un `Spell` : `SpellCastingTest` (4 helpers), `CharacterInstanceGrantedSpellsTest` (1 helper) — ajouter `1`.

### 2. `game/catalog/SpellCatalog.java`
Dans `warmSpells()` : après le remplissage de la map, ajouter une validation fail-fast par famille (groupée par `name`) : tiers contigus à partir de 1, `requiredLevel` strictement croissant entre tiers — même style que la validation eager des loot tables (`MonsterCatalog.registerTemplates()`). Ajouter aussi une garde anti-collision d'UUID avant le `spells.put(...)` (un copier-coller d'ID lors de l'écriture des ~100 nouvelles entrées serait sinon silencieusement écrasé).
`spellsLearnableAt(class, level)` : aucun changement, le match exact sur `requiredLevel` reste correct.

### 3. `domain/actor/component/SpellCasting.java`
`learn(Spell)` (actuellement `knownSpells.add(spell)`, `boolean`) devient une upgrade-in-place : si un sort de même `name` est déjà connu, le remplacer par le nouveau (sinon simple ajout). Remplacer le retour `boolean` par un enum `LearnResult { NEW, UPGRADED, ALREADY_KNOWN }` pour que l'appelant sache s'il doit notifier une "montée en puissance" plutôt qu'un premier apprentissage.
`nextCastAt` n'a besoin d'aucun changement : la nouvelle entrée a un nouvel UUID donc pas de cooldown hérité (le sort amélioré est immédiatement castable — comportement voulu). L'entrée orpheline de l'ancien tier reste en mémoire mais c'est négligeable (borné à ~5 tiers × ~24 familles par instance de perso, elle-même transitoire).

### 4. Persistance de l'upgrade
- `domain/actor/event/CharacterLearnedSpell.java` : ajouter un champ `Spell previousTier` (nullable, `null` si `NEW`).
- `game/engine/SpellLearningEngine.java` (`learnSpellsAt`, L31-39) : switch sur `LearnResult`, publie l'event pour `NEW` et `UPGRADED` (pas pour `ALREADY_KNOWN`), en passant l'ancien sort comme `previousTier` si upgrade.
- `persistence/CharacterSpellDao.java` : ajouter `replace(characterId, oldSpellId, newSpellId)` — `DELETE` de l'ancienne ligne + `INSERT` de la nouvelle, dans `dsl.transaction(...)` (compound statement, pas encore de transaction ailleurs dans ce DAO).
- `persistence/listener/SpellPersistenceListener.java` (`onCharacterLearnedSpell`) : si `event.previousTier() != null` → `characterSpellDao.replace(...)`, sinon `insert(...)` comme aujourd'hui.

### 5. Rattrapage pour les persos déjà en jeu
`WorldInstanceService.enterGame()` n'existe plus (retiré au commit `65c3b80`) — les sorts connus sont résolus dans `CharacterDao.toDomain()` à la construction, et le point d'entrée en jeu est `CharacterSelect.onReceive()` (`network/command/charselect/CharacterSelect.java:49-75`).
Ajouter `SpellLearningEngine.reconcile(CharacterInstance character)` (méthode publique simple, pas un `@EventListener`) qui rejoue `learnSpellsAt(character, level)` pour `level` de 1 à `character.getLevel()` — sûr et bon marché car `learn()` est maintenant idempotent (`ALREADY_KNOWN` ne publie rien). Appeler `spellLearningEngine.reconcile(loadedChar)` dans `CharacterSelect.onReceive()` juste après `connection.attachCharacter(loadedChar)` (ligne 67), avant `loadPlayer`. Injecter `SpellLearningEngine` dans le constructeur de `CharacterSelect` (4ème param actuel → 5ème).
`CharacterCreate` n'a pas besoin de cet appel : un perso niveau 1 neuf est déjà couvert par `NewGamePlayerCreated`.

### 6. Message client
`network/message/ingame/SpellLearned.java` (actuellement `record SpellLearned(String spellName)`) → ajouter `int tier, boolean upgraded`. Câblé depuis `SpellPersistenceListener` : `new SpellLearned(event.spell().name(), event.spell().tier(), event.previousTier() != null)`. Champ additif au JSON, non-bréquant côté client tolérant.

### 7. `network/command/ingame/Cast.java`
Aucun changement nécessaire : `resolveKnownSpell` matche par nom sur `knownSpells()`, qui ne contient plus qu'un seul `Spell` par famille ; range/cooldown/mana sont déjà lus directement sur le `Spell` résolu (donc sur les valeurs du tier courant).
Note pour info seulement, hors scope : ambiguïté préexistante si un objet accorde un sort du même nom qu'un sort appris à un tier différent (`resolveKnownSpell` concatène `knownSpells()` et `getGrantedSpells()`) — pas nouveau, pas traité ici.

### 8. Données — `spells.json`
Nouvel ordre de champs : `id, name, tier, description, requiredLevel, manaCost, cooldownSeconds, range, effect, effectDice, classes[, modifiedStat, durationSeconds]`.
Étendre les 24 familles existantes en appliquant la formule de scaling ci-dessus, ex. Fire Bolt (reqLvl 1 → tiers à 1/5/9/13/17), Fireball (reqLvl 5 → plafonné à 4 tiers faute de niveau 21), Shield of Faith (valeur plate `effectDice` incrémentée de 1/tier, pas de code à toucher — `DiceExpression` parse déjà la notation plate), Cure Wounds (`1d8+3` → `2d8+3` → ..., modificateur fixe).

## Ordre d'implémentation recommandé

1. `Spell`/`SpellDefinition` + 3 call-sites de test — compile, comportement inchangé.
2. Validation de tiers dans `SpellCatalog` — passe trivialement tant que tout est encore tier 1.
3. `SpellCasting.learn` → `LearnResult` + adaptation de `SpellLearningEngine` — toujours `NEW` tant que les données sont mono-tier.
4. Event/listener/DAO pour l'upgrade + message `SpellLearned` enrichi — testable avec un fixture 2-tiers synthétique avant de toucher aux vraies données.
5. `SpellLearningEngine.reconcile` + branchement dans `CharacterSelect`.
6. Écriture des nouveaux tiers dans `spells.json` pour les 24 familles — dernière étape, active la fonctionnalité de bout en bout une fois tout le mécanisme validé.

## Vérification

- `mvn test` : `SpellLearningEngineTest`, `SpellCastingTest`, `CharacterInstanceGrantedSpellsTest` doivent passer après les changements de signature (item 1, 3) ; ajouter un test couvrant `LearnResult.UPGRADED` (apprentissage tier1 puis tier2 → remplacement, cooldown reset, event avec `previousTier` renseigné) et un test de validation `SpellCatalog` (famille avec tiers non contigus → `IllegalStateException` au warm-up).
- `mvn spring-boot:run`, se connecter en TCP/JSON sur le port 4002, créer un perso niveau 1, vérifier `SpellLearned` avec `tier=1, upgraded=false` ; faire monter le perso au niveau du tier 2 (XP ou commande de debug si dispo) et vérifier réception d'un `SpellLearned` avec `upgraded=true`, puis `cast <sort>` pour confirmer que dégâts/mana/cooldown reflètent bien le nouveau tier.
- Vérifier le rattrapage : sur un perso existant en base déjà au-delà du niveau du tier 2 (créé avant ce changement), se reconnecter via `character-select` et confirmer que le tier est bien mis à niveau sans attendre un nouveau level-up.

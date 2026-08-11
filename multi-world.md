# Multi-World : plan des phases restantes

Ce document fait suite au plan initial (Lobby/WorldTemplate/WorldInstance). Les
Phases A, B, C, D et E sont **terminées**. Il ne reste aucune phase planifiée
à ce jour ; ce fichier garde trace des identifiants/décisions figés en
Phase A/B/C/D/E pour toute suite éventuelle.

## État à date (fin Phase E)

- **Phase A** — `WorldTemplate`/`RoomTemplate`/`RoomTemplatePortal`/
  `NpcTemplate` + `WorldTemplateService` (charge `data/worlds/*/` via
  `PathMatchingResourcePatternResolver`). Contenu existant migré vers
  `data/worlds/default/`. Comportement joueur inchangé.
- **Phase B** — `WorldInstance` (persistée, table `world_instance` +
  `world_instance_member`), `RoomInstance` (renommage de l'ancien `Room`,
  id déterministe), `WorldInstanceService` (matérialisation du graphe de
  rooms par instance). Une seule instance existe encore — l'instance par
  défaut — chargée avec empressement au boot. `RoomService` a été réduit
  aux `@EventListener` + quelques délégations minces vers
  `WorldInstanceService` (gardées uniquement pour ne pas casser la vaste
  surface de tests qui appelle `roomService.warmRooms()` comme point
  d'entrée de bootstrap).
- **Phase C** — `ConnectionState` gagne `LOBBY`/`CHARSELECT` (renommage
  d'`AUTHED` + nouvel état), `CharacterSelectionWorld` (nouveau, même forme
  qu'`AuthWorld`/`GameWorld`), `controller/lobby/{WorldsList,WorldEnter}`,
  `controller/charselect/{CharacterCreate,CharacterSelect,CharacterDelete,
  CharSelectStatus}`. Voir "Écarts par rapport au plan initial" ci-dessous
  pour ce qui a changé en cours de route par rapport à la version du plan
  écrite en fin de Phase B.
- **Phase D** — `domain/Party`/`PartyMember` (mémoire uniquement, jamais
  persisté) + `game/PartyService`, commandes `party-{create,invite,accept,
  leave,kick}` (état `LOBBY`), `WorldInstanceService.createInstance`
  (+ événement `WorldInstanceCreated`) pour matérialiser une `WorldInstance`
  neuve par party, `world-enter` étendu avec un vrai chemin party en plus du
  bypass solo de la Phase C. Monde fixture `data/worlds/arena/` (2 rooms,
  minPlayers=2/maxPlayers=4) prouvant l'isolation entre deux instances d'un
  même template.
- **Phase E** — `GameWorld.onlineCharactersInWorldInstance(UUID)` (filtre
  `characters.values()` par `character.getWorldInstanceId()`) et
  `WorldInstanceService.broadcastToInstance(WorldInstance, OutputMessage,
  GamePlayer exclude)` (délègue à `RoomInstance.broadcast` room par room de
  l'instance). `RestService.shortRest`/`longRest` utilisent désormais le
  premier pour ne soigner que les joueurs en ligne de la `WorldInstance` de
  l'initiateur (au lieu de `gameWorld.onlineCharacters()`, global à tout le
  process) ; `CharacterService.onShortRestTaken`/`onLongRestTaken` utilisent
  le second pour l'annonce (`ShortRestAnnounced`/`LongRestAnnounced`),
  remplaçant leur précédente boucle manuelle sur `gameWorld.onlineCharacters()`
  — `CharacterService` n'a donc plus besoin de `GameWorld` du tout, seulement
  de `WorldInstanceService`. `V10__add_unique_character_world_instance.sql`
  ajoute `UNIQUE (account_id, world_instance_id)` sur `character` (déjà
  garanti côté application depuis la Phase C, voir `CharacterCreate`).

### Identifiants fixes à connaître

- `WorldTemplate` par défaut : `f128833b-9a8a-4fb9-9796-33fd9413490d`
  (`data/worlds/default/world.json`).
- `WorldInstance` par défaut : `WorldInstance.DEFAULT_ID` =
  `a8e98a8e-73c1-43dd-b36e-a2f67f00ff48` (créée par
  `V8__add_character_world_instance.sql`).
- Id de `RoomInstance` : `RoomInstance.deterministicId(worldInstanceId,
  roomTemplateId)` — UUID v3, jamais aléatoire. `character.current_room_id`
  et `item.room_id` retiennent des sens différents : le premier reste un id
  de **RoomTemplate** (indépendant de l'instance, voir
  `RoomService.onGamePlayerMovedToRoom`/`onGamePlayerSpawnedToRoom`), le
  second un id de **RoomInstance** (voir `V9__RecomputeDefaultInstanceItemRoomIds`).
  Ne jamais confondre les deux en écrivant du nouveau code — c'est la
  source d'erreur la plus probable dans les phases qui suivent.
- `WorldInstanceService.warmDefaultInstance()` **force toujours** une
  reconstruction complète (contrairement à `getOrMaterialize`, qui respecte
  le cache résident) — nécessaire pour que `roomService.warmRooms()` continue
  de donner un graphe neuf à chaque appel dans les tests (contexte Spring mis
  en cache entre classes). Toute nouvelle méthode de rechargement doit
  respecter cette distinction.
- `V9` est déjà pris par une migration **Java** (`src/main/java/db/migration/
  V9__RecomputeDefaultInstanceItemRoomIds.java`, Phase B) — invisible en
  listant seulement `src/main/resources/db/migration/*.sql`, découvert en
  Phase E via un échec Flyway ("Found more than one migration with version 9")
  à la première tentative de réutiliser V9 pour une migration SQL. La
  prochaine migration est donc `V10`, pas `V9` malgré ce que suggère un simple
  `ls` du dossier SQL — toujours vérifier aussi `src/main/java/db/migration/`
  avant de choisir un numéro de version.

### Flakiness du mannequin d'entraînement — corrigée

`RestServiceTest`/`RestTest`, tests "in combat" (`shortRestIsRefusedInCombat
AndNothingChanges`, `longRestIsRefusedInCombatAndConsumesNothing`,
`shortRestInCombatIsRefused`) étaient **flaky** (~1 échec sur 4 en relances
isolées pendant la Phase E, sur différents tests de ce groupe à chaque run).

Root cause identifiée (pas un problème de seed, malgré la piste initialement
envisagée) : `combatEngine.attack(character, monster)` déclenche, dans le même
appel, la riposte du monstre dès que l'action économique du niveau 1 est
épuisée (`CombatEngine.performTurnAttack` → `continueOrEndTurn` → `cascade` →
`resolveFromCurrentTurn`). Le mannequin de ces tests avait `naturalArmorClass
= -1000` pour garantir que le *joueur* le touche toujours, mais rien
n'empêchait le mannequin de toucher le *joueur* en retour — et un 20 naturel
touche toujours quelle que soit la CA (`DiceRoller#resolveHit`, règle 5e),
donc même un modificateur d'attaque très négatif ne ramène jamais ce risque à
zéro (~5% par attaque). Le personnage étant à 1 PV dans ces tests, un seul
coup encaissé le blesse ou le tue et fait échouer les assertions "rien n'a
changé".

Fix (dans `RestServiceTest`/`RestTest`, pas dans le code de production) : FOR
`-10` sur le mannequin (`TestAttributes.of(-10, 10, 10, 10, 10, 10)`) plutôt
que de toucher `CombatEngine`/`DiceRoller` ou d'y introduire un seed — un
modificateur de dégâts de -10 garantit que même un coup critique (dégâts
doublés, 2d4 dans ce cas, max 8) retombe à 0 via le plancher `Math.max(0, ...)`
de `GameMonster#rollDamage` : le mannequin peut désormais toujours *toucher*
(5% du temps sur un 20 naturel) mais ne peut plus jamais *blesser*. 8 relances
isolées de `RestServiceTest`+`RestTest` après le fix : 0 échec (vs ~1 sur 4
avant).

---

## Phase C — États `LOBBY`/`CHARSELECT`, pas encore de Party ✅ Terminée

Objectif (atteint) : le joueur transite par un Lobby après login, mais comme
un seul `WorldTemplate` existe encore, `world-enter` n'a qu'un chemin solo
(bypass "tu as déjà un personnage ici").

### `ConnectionState`

```java
public enum ConnectionState {
    CONNECTED, LOBBY, CHARSELECT, INGAME
}
```

`AUTHED` → `LOBBY` (renommage, sémantique différente : plus "choisir un
personnage" mais "choisir un World"). `CHARSELECT` est nouveau : "une
`WorldInstance` est résolue, reste à choisir/créer son personnage dedans."
`AuthWorld` garde la connexion enregistrée (compte) tout du long de `LOBBY`
et `CHARSELECT` sans jamais la retirer elle-même ; seul
`AuthWorld.moveToGameWorld` (`CHARSELECT` → `INGAME`) la retire, et
`Logout` la ré-enregistre explicitement au retour en jeu (voir plus bas).

### Composants livrés

- `game/CharacterSelectionWorld.java` — `Map<Connection, WorldInstance>`,
  même forme que `AuthWorld`/`GameWorld` : `enterWorld(Connection,
  WorldInstance)` pousse `CHARSELECT`, `exitWorld(Connection)` repasse à
  `LOBBY`. Threadée à travers toute la chaîne telnet (`TelnetServer` →
  `TelnetServerInitializer` → `TelnetSessionHandler` → `TelnetConnection`) ;
  `TelnetConnection.handleClose()` l'appelle aussi inconditionnellement (même
  motif que `gameWorld.exitWorld`/`authWorld.exitWorld`) pour ne jamais
  laisser une entrée résiduelle si la connexion tombe pendant `CHARSELECT`.
- `controller/lobby/WorldsList.java` (`worlds-list`, état `LOBBY`) : liste
  les `WorldTemplate`, et pour chacun, si `WorldInstanceDao
  .findByAccountIdAndWorldTemplateId` + `CharacterDao
  .findByAccountIdAndWorldInstanceId` renvoient un résultat, affiche "tu as
  un personnage ici : nom, classe, niveau" (`network/message/lobby
  /WorldsList.Entry`, un seul champ optionnel plutôt que deux records).
- `controller/lobby/WorldEnter.java` (`world-enter <short-name>`, état
  `LOBBY`) : chemin solo uniquement (voir "Écarts" ci-dessous pour le détail
  du bypass). Pousse la connexion en `CHARSELECT` liée à la `WorldInstance`
  trouvée, puis affiche le statut via `CharSelectStatus`.
- `controller/charselect/{CharacterCreate,CharacterSelect,CharacterDelete}
  .java` (déplacés depuis `controller/authed/`), `states()` = `CHARSELECT`,
  chaque constructeur gagne `CharacterSelectionWorld` pour résoudre la
  `WorldInstance` courante.
- `controller/charselect/CharSelectStatus.java` (nouveau, pas un
  `ControllerHandler` — jamais invocable directement par le joueur) :
  remplace le rôle de "relist" que jouait l'ancien `characters-list`,
  affichant `ExistingCharacterInWorld`/`NoCharacterInWorld` après chaque
  transition/action en `CHARSELECT`.

### Écarts par rapport au plan initial

Ces décisions ont été prises pendant l'implémentation, sans être anticipées
par la version du plan écrite en fin de Phase B :

- **`controller/authed/CharacterList.java` (`characters-list`) supprimé sans
  remplaçant-commande**, pas juste déplacé : la section "Simplification"
  ci-dessous notait déjà qu'un vrai listing n'a plus de sens à 1 personnage
  max par instance — la conclusion logique est qu'aucune commande de listing
  n'est plus nécessaire du tout, seulement un statut affiché automatiquement
  aux points de transition (`WorldEnter`, après `character-create`/
  `character-delete`). C'est le rôle de `CharSelectStatus`.
- **`network/message/authed/` renommé en `network/message/charselect/`**
  pour suivre le renommage du package contrôleur (convention du projet :
  un état ↔ un sous-package message). `CharacterList` (le message) et
  `CharacterAlreadyExists` sont supprimés — `CharacterAlreadyExists`
  signifiait "ce nom est déjà pris par ce compte" (un contrôle qui n'a plus
  de sens une fois la règle "1 perso par instance" appliquée) ; il est
  remplacé par `ExistingCharacterInWorld`, qui porte le vrai message métier
  ("tu as déjà un personnage ici"). Deux messages neufs :
  `NoCharacterInWorld`/`ExistingCharacterInWorld`.
- **`GameWorld.isCharacterNameTaken` supprimé** (plus aucun appelant) plutôt
  que scopé par instance comme le bullet Phase E le prévoyait : le nouveau
  contrôle `characterDao.findByAccountIdAndWorldInstanceId(...).isPresent()`
  dans `CharacterCreate` le rend redondant — impossible d'atteindre un
  conflit de nom au sein d'une instance sans être déjà bloqué par la règle
  "1 perso par instance" en amont. Le bullet correspondant a été retiré de
  la Phase E ci-dessous.
- **`GameWorld.createCharacter` gagne un paramètre `WorldInstance` explicite**
  (`createCharacter(Account, WorldInstance, String, Gender, Race,
  CharacterClass)`) plutôt que de continuer à résoudre la room de départ via
  `roomService.startingRoom()` (scopée à `WorldInstance.DEFAULT_ID` en dur) —
  sans ce changement, un personnage créé après un `world-enter` sur une autre
  instance que l'instance par défaut aurait spawné dans la mauvaise
  instance. Nécessaire dès la Phase C malgré le bypass solo, puisque
  `CharacterCreate` a déjà la `WorldInstance` sous la main via
  `CharacterSelectionWorld`.
- **`character-select` devient sans argument** (tout argument fourni est
  ignoré) plutôt que de garder `<name>` : la section "Simplification"
  prévoyait déjà qu'il n'y a jamais besoin de désambiguïser vu la règle "1
  perso par instance" — seuls `character-create <name>` et
  `character-delete <name>` gardent leur argument (un nom reste nécessaire
  pour créer/cibler).
- **`Logout` (état `INGAME`) réenregistre explicitement le compte dans
  `AuthWorld`** avant `characterSelectionWorld.enterWorld` — pas mentionné
  dans le plan initial mais nécessaire : `AuthWorld.moveToGameWorld` retire
  la connexion de son `Map` au moment du passage `CHARSELECT` → `INGAME`,
  donc `authWorld.account(connection)` ne résout plus rien tant que le
  logout ne la réinscrit pas (repris tel quel de l'ancien code `AUTHED`, qui
  faisait déjà ce même appel).
- **`controller/Logout.java` (état `CHARSELECT` → `LOBBY`) envoie
  `network/message/lobby/BackInLobby`** (nouveau message, non prévu dans le
  plan qui ne détaillait pas le contenu des messages).

### DB / DAO livrés

- `WorldInstanceDao.findByAccountIdAndWorldTemplateId(accountId,
  worldTemplateId)` → `Optional<WorldInstance>`, sous-requête sur
  `world_instance_member` plutôt qu'un `JOIN` explicite (cohérent avec le
  style `selectFrom` déjà utilisé par `findById`).
- `CharacterDao.findByAccountIdAndWorldInstanceId(accountId,
  worldInstanceId)` → `Optional<GamePlayer>`, remplace `findByAccountId`
  (aucun appelant restant après la suppression de `characters-list`).
- `CharacterDao.findByAccountIdAndName` gagne le paramètre `worldInstanceId`.
- `UNIQUE (account_id, world_instance_id)` sur `character` **pas ajoutée** —
  reportée à la Phase E comme prévu par le plan (la règle "1 perso par
  instance" reste appliquée uniquement côté application pour l'instant, via
  les contrôles dans `CharacterCreate`/`CharacterSelect`).

### Vérification

- `mvn test` vert (280 tests ; le seul échec observé pendant le
  développement est la flakiness déjà documentée ci-dessus, reproduite puis
  confirmée non liée à ce chantier par relance isolée).
- Nouveaux tests : `WorldsListTest`, `WorldEnterTest`, `CharacterSelectTest`,
  `CharacterDeleteTest`, `LogoutTest` (`Logout` n'avait aucune couverture
  avant cette phase). Tests existants mis à jour :
  `CharacterCreateTest`/`LoginTest`/`GameWorldTest`/`CharacterDaoTest`/
  `TelnetConnectionTest`/`TelnetSessionHandlerTest`/
  `TelnetServerInitializerTest`.
- Test manuel telnet du parcours complet (`login → LOBBY → worlds-list →
  world-enter default → CHARSELECT → character-select (ou create) →
  INGAME`) **pas encore fait** — à faire avant de considérer la Phase C
  définitivement validée en conditions réelles, en plus des tests
  automatisés.

---

## Phase D — `PartyService` + vrai lancement multi-instance ✅ Terminée

Objectif (atteint) : plusieurs `WorldInstance` du même `WorldTemplate`
peuvent coexister, chacune scopée à une party, invisibles entre elles.

### Composants livrés

- `domain/Party.java` — `id` (éphémère), `leaderAccountId` (mutable : promu au
  membre suivant par ordre d'arrivée quand le leader part via `remove`,
  jamais de dissolution tant qu'il reste au moins un membre),
  `members: List<PartyMember>`, `pendingInvites: Set<UUID>`.
- `domain/PartyMember.java` — **`accountId` uniquement**, pas de `Connection`
  stockée : la connexion vivante se résout à la demande (voir "Écarts"
  ci-dessous pour le détail du mécanisme de résolution). Permet nativement la
  règle "un membre déconnecté bloque le lancement" (pas de nettoyage
  automatique à la déconnexion) : au moment du `world-enter`, chaque membre
  est résolu vers sa connexion courante ; un membre sans connexion active en
  `LOBBY` fait échouer le lancement (`network/message/lobby/MemberOffline`)
  avec un message explicite invitant le leader à `party-kick`.
- `game/PartyService.java` (nouveau, mémoire uniquement, jamais persisté) —
  `Map<UUID, Party> partiesById`, `Map<UUID, UUID> partyIdByAccountId`,
  `Map<UUID, UUID> partyIdByPendingInvite` (un seul invite pendant à la fois
  par compte, la party précédente est simplement écrasée par une invite
  suivante). `createParty`, `invite`, `accept`, `leave`/`kick` (délèguent
  tous deux à un `remove` privé partagé — `Party.remove` gère déjà la
  promotion/dissolution identiquement dans les deux cas), `dissolve` (appelé
  juste après un `world-enter` réussi). Les deux maps secondaires
  s'auto-nettoient sans passe explicite : une fois une party dissoute/vidée,
  toute entrée qui pointait encore vers son id résout vers `Optional.empty()`
  via l'indirection `resolve(partyId)`.
- Commandes `controller/lobby/{PartyCreate,PartyInvite,PartyAccept,
  PartyLeave,PartyKick}` (état `LOBBY`), chacune avec son couple de messages
  sous `network/message/lobby/` (~20 records neufs pour les différentes
  issues : succès, cible introuvable/hors-ligne/déjà en party, pas leader,
  etc.) — toute la validation (leader, cible en ligne, appartenance) vit dans
  le contrôleur, `PartyService` reste une collection de primitives sans
  logique métier, même répartition que `WorldEnter`/`CharacterCreate`.
- `WorldInstanceService.createInstance(WorldTemplate, Set<UUID>
  memberAccountIds, UUID leaderAccountId)` — construit une `WorldInstance`
  neuve, la matérialise **avant** de publier `WorldInstanceCreated` (mémoire
  d'abord, événement de persistance ensuite, même ordre que le reste du
  domaine) ; l'`@EventListener onWorldInstanceCreated` fait
  `worldInstanceDao.insert(...)`.
- `WorldEnter` réécrit avec deux chemins : `partyService.partyOf(accountId)`
  vide → bypass solo de la Phase C inchangé (réutilise l'instance existante
  du compte pour ce template, ou l'instance par défaut) ; présent → chemin
  party (voir "Écarts" pour la règle exacte de sélection entre les deux).
- Monde fixture `data/worlds/arena/` (2 rooms reliées par un portail,
  `minPlayers=2`/`maxPlayers=4`, aucun NPC/monstre) — utilisé par
  `WorldEnterTest` pour prouver l'isolation bout en bout entre deux parties
  qui lancent le même `WorldTemplate`.

### Écarts par rapport au plan initial

- **`AuthWorld.findConnectionByAccountId` ajoutée en plus de
  `findConnectionByLogin`** (le plan ne mentionnait que la seconde) :
  `PartyMember` ne retient que l'`accountId` (jamais de login), donc résoudre
  la connexion courante d'un *membre* (via son `accountId`, dans la boucle de
  `WorldEnter`) a besoin d'un lookup par id de compte, pas par login.
  `findConnectionByLogin` reste utilisée là où l'humain tape effectivement un
  login en argument (`party-invite <login>` pour trouver la cible à
  notifier, `party-kick <login>` reste résolu via `AccountDao.findByLogin`
  puisqu'une cible hors-ligne n'a justement pas d'entrée dans `AuthWorld`).
- **Règle de sélection solo/party dans `world-enter` précisée** : le plan
  disait "la taille de la party, ou 1 si pas de party" sans trancher le cas
  d'une party solitaire (leader seul, aucune invite acceptée). Décision prise
  à l'implémentation : dès qu'une `Party` existe pour le compte (même de
  taille 1), le chemin party s'applique **toujours** — une `WorldInstance`
  neuve est matérialisée via `createInstance`, sans consulter le bypass
  "instance déjà connue" de la Phase C. Seule l'absence totale de party
  déclenche le bypass solo. Un joueur qui veut simplement reprendre son
  personnage existant ne doit donc pas faire `party-create` avant
  `world-enter`.
- **`TooManyPlayers` ajoutée** (non détaillée dans le plan, qui ne
  mentionnait que `minPlayers`) : `world-enter` en party rejette aussi une
  taille de party dépassant `template.maxPlayers()`, symétrique au contrôle
  `minPlayers` déjà prévu — `party-invite` n'a lui-même aucune limite de
  taille (une party n'est rattachée à aucun `WorldTemplate` avant le
  lancement), donc ce contrôle ne peut se faire qu'à ce point-là.

### Vérification

- `mvn test` vert (324 tests). Seul échec observé : la flakiness déjà
  documentée ci-dessus (`RestServiceTest`/`RestTest`, dés de combat non
  seedés), reproduite à nouveau pendant ce développement et toujours sans
  rapport avec ce chantier.
- Nouveaux tests : `PartyTest` (domaine, JUnit pur), `PartyCreateTest`,
  `PartyInviteTest`, `PartyAcceptTest`, `PartyLeaveTest`, `PartyKickTest`,
  et `WorldEnterTest` étendu (chemin party : non-leader, effectif
  insuffisant, membre hors-ligne, lancement réussi, isolation entre deux
  parties via `data/worlds/arena/`). `WorldsListTest` mis à jour pour
  filtrer sur `shortName == "default"` plutôt que d'exiger un `worlds()` de
  taille 1, maintenant que `arena` apparaît aussi dans la liste.
- Piège de test découvert et corrigé en cours de route : `AuthWorld` (comme
  les autres `game/*World`) est un singleton Spring dont la map interne n'est
  **jamais réinitialisée entre tests** (seul le contenu DB est retiré par
  `@Transactional`) — avant cette phase ça ne posait aucun problème
  puisqu'aucun code ne cherchait une connexion par login à travers plusieurs
  connexions. `findConnectionByLogin` change ça : deux classes de test
  utilisant le même login générique (`"leader1"`, `"member1"`...) peuvent
  désormais faire résoudre `party-invite` vers la mauvaise connexion, restée
  résidente d'un test précédent. Tous les logins des tests `Party*Test`/
  `WorldEnterTest` (nouveaux cas) portent donc un préfixe par classe
  (`pc-`, `pi-`, `pa-`, `pl-`, `pk-`, `we-`) pour rester globalement uniques
  sur toute la suite.
- Test manuel telnet à deux connexions simultanées **pas encore fait** — à
  faire avant de considérer la Phase D définitivement validée en conditions
  réelles (voir "Vérification globale" ci-dessous), en plus des tests
  automatisés.

---

## Phase E — Finitions de scoping ✅ Terminée

Objectif (atteint) : le repos court/long, dernier système à encore raisonner
"tout le process" via `GameWorld.onlineCharacters()`, devient scopé à la
`WorldInstance` de l'initiateur — plusieurs parties lancées sur le même ou des
`WorldTemplate` différents ne se soignent/s'annoncent plus mutuellement leurs
repos. La règle "1 personnage par (compte, WorldInstance)", appliquée côté
application depuis la Phase C, est en plus verrouillée en base.

### Composants livrés

- `GameWorld.onlineCharactersInWorldInstance(UUID worldInstanceId)` (nouveau,
  à côté de `onlineCharacters()`) — filtre `characters.values()` par
  `character.getWorldInstanceId()`. `RestService.shortRest`/`longRest`
  l'utilisent, avec `initiator.getWorldInstanceId()`, pour construire
  `healedAmounts` à la place de `gameWorld.onlineCharacters()`.
- `WorldInstanceService.broadcastToInstance(WorldInstance instance,
  OutputMessage message, GamePlayer exclude)` (nouveau) — itère
  `instance.roomInstances()` et délègue à `RoomInstance.broadcast(message,
  exclude)` room par room, plutôt que de repasser par `GameWorld` (qui ne
  connaît que les connexions, pas la répartition par room).
  `CharacterService.onShortRestTaken`/`onLongRestTaken` l'utilisent pour
  `ShortRestAnnounced`/`LongRestAnnounced` (résolution de la `WorldInstance`
  via `worldInstanceService.getOrMaterialize(event.initiator()
  .getWorldInstanceId())`), à la place de leur précédente boucle manuelle
  `gameWorld.onlineCharacters().forEach(character -> character.send(...))`.
  Conséquence : `CharacterService` n'a plus aucun usage de `GameWorld` et perd
  sa dépendance à ce bean, remplacée par `WorldInstanceService`.
- `V10__add_unique_character_world_instance.sql` — `ALTER TABLE character ADD
  CONSTRAINT uniq_character_account_world_instance UNIQUE (account_id,
  world_instance_id)`. Sûr sans backfill : `CharacterCreate` refuse déjà toute
  création en doublon depuis la Phase C (`characterDao
  .findByAccountIdAndWorldInstanceId(...).isPresent()`), donc aucune ligne
  existante ne peut violer la contrainte.

### Écarts par rapport au plan initial

- **Numéro de migration `V9` déjà pris** par une migration Java existante
  (`V9__RecomputeDefaultInstanceItemRoomIds`, Phase B) invisible dans
  `src/main/resources/db/migration/` — la nouvelle migration est donc `V10`,
  pas `V9` comme le plan l'aurait suggéré. Voir "Identifiants fixes" ci-dessus.
- Le plan ne précisait pas explicitement que `broadcastToInstance`
  remplacerait la boucle de `CharacterService` (seul `RestService` était cité
  comme appelant) — décision prise à l'implémentation puisque
  `gameWorld.onlineCharacters()` apparaissait aussi dans
  `onShortRestTaken`/`onLongRestTaken`, pas seulement dans `RestService`, et
  qu'un broadcast room-par-room est la manière idiomatique d'envoyer un
  message à toute une `WorldInstance` (même mécanisme que
  `RoomInstance.broadcast` déjà utilisé pour une seule room).

### Vérification

- `mvn test` : 324 tests, 0 échec imputable à cette phase. Les seuls échecs
  observés pendant le développement (`RestServiceTest`/`RestTest`, tests "in
  combat") reproduisaient la flakiness déjà documentée en fin de Phase D
  (dés de combat non seedés) — confirmée non liée à ce chantier par 4
  relances isolées de
  `RestServiceTest#longRestIsRefusedInCombatAndConsumesNothing` (1 échec sur
  4), puis **corrigée dans la foulée** (root cause + fix : voir "Flakiness du
  mannequin d'entraînement — corrigée" ci-dessus). 8 relances isolées de
  `RestServiceTest`/`RestTest` après le fix : 0 échec.
- `mvn spotless:check` vert.
- Pas de nouveau test dédié à `onlineCharactersInWorldInstance`/
  `broadcastToInstance` en isolation : la couverture existante de
  `RestServiceTest`/`RestTest` (repos avec plusieurs joueurs en ligne) exerce
  déjà les deux chemins de bout en bout, tous les personnages de test étant
  rattachés à `WorldInstance.DEFAULT_ID`. Pas de test prouvant l'isolation
  entre deux `WorldInstance` distinctes pour le repos spécifiquement (contrai-
  rement à `WorldEnterTest` pour le lancement) — à ajouter si ce chantier est
  repris.

## Vérification globale (toutes phases confondues)

- `mvn test` vert après chaque phase, jamais un backlog de rouge accumulé
  entre deux phases.
- Un scénario manuel telnet à deux connexions simultanées (deux clients
  telnet dans deux terminaux) pour la Phase D : party à deux, lancement,
  vérifier qu'un troisième client sur une party différente ne voit ni
  n'affecte les deux premiers. **Pas encore fait** malgré la Phase D
  terminée côté code/tests automatisés — voir sa section "Vérification".

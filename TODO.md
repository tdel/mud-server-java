# État des lieux et feuille de route des systèmes DnD5e

## Contexte

Le projet est un MUD Java/Spring Boot qui vise à reprendre les règles DnD5e. Cet état des lieux liste ce qui existe déjà dans le code, puis ce qui manque ou reste superficiel, organisé par système de jeu avec un ordre de dépendance suggéré. C'est une référence, pas un plan d'implémentation détaillé d'une fonctionnalité précise.

## Ce qui existe déjà (pour référence, ne pas refaire)

- **Comptes/session** : login/register/logout/quit, BCrypt, un seul virtual thread par connexion avec queue ordonnée.
- **Personnage** : création avec génération 4d6-drop-lowest (`CharacterCreate.java`), 4 races (`Race.java`) avec bonus de caractéristiques DnD5e corrects, `Attribute` (les 6 caractéristiques), modificateur `(score-10)/2` calculé (`GameCharacter.getModifier`).
- **Classe de personnage** : enum `CharacterClass` (12 classes DnD5e), choisie à la création (`ChooseClass`), données par classe (`game/actor/ClassService.java` + `data/class.json`) : dé de vie (`hitDie`), or de départ (dés + multiplicateur), jets de sauvegarde maîtrisés et compétences maîtrisées (liste fixe par classe, pas de choix joueur). Pas d'accès aux sorts par classe — cette donnée n'existe pas encore.
- **Bonus de maîtrise** : `GamePlayer.getProficiencyBonus()` (`2 + (niveau-1)/4`), appliqué aux jets d'attaque, aux jets de compétence/sauvegarde et affiché dans `stats`. Les monstres utilisent un bonus fixe (+2), n'ayant pas de progression de niveau/CR.
- **Compétences et jets de sauvegarde** : `game/CheckService.java` résout un jet de compétence (`check <skill> <dc>`) ou de sauvegarde (`save <attribute> <dc>`) — 1d20 + modificateur de la caractéristique gouvernante + bonus de maîtrise si la classe est proficient (`ClassService.skillProficiencies`/`savingThrowProficiencies`), comparé à une DC fournie par le joueur. Pas de règle de critique sur 1/20 naturel (propre aux jets d'attaque en DnD5e RAW). Affiché aussi dans `stats`/`examine` (sections « Saving Throws »/« Skills »).
- **Niveau / XP** : `GamePlayer.level`/`xp`, gain via `gainXp` (événement `CharacterGainedXp`) crédité au tueur d'un monstre (`MonsterTemplate.xpReward`). Seuils par niveau dans `game/actor/LevelService.java` + `data/levels.json`. Montée de niveau (`CharacterService.onCharacterGainedXp`) : PV gagnés = `hitDie/2 + 1 + mod CON`, plusieurs niveaux possibles en un seul kill, diffusion `PlayerLeveledUp`.
- **Or** : `PlayerInventory.gold`, tiré à la création selon la classe (`GameWorld.createCharacter`), persisté (`V3__add_gold.sql`), affiché uniquement via `inventory` (volontairement absent de `stats`/`examine`). Varie déjà en jeu par deux voies — voir « Or (complément) » plus bas.
- **Points de vie réels** : PV max au niveau 1 = `hitDie` + mod CON (plus figés à 100/100). `currentHealth` est modifié par les dégâts de combat, la mort/respawn et la montée de niveau.
- **Monde** : `Room`/`RoomExit` en cache mémoire chaud, navigation (`go`), description (`look`), broadcast d'arrivée/départ.
- **Objets** : `ItemTemplate`/`Item`, 10 types, 6 slots d'équipement, take/drop/equip/unequip avec gestion de concurrence (`synchronized(item)`, événements de domaine + projection DB). Armures : catégorie (léger/moyen/lourd) + CA de base ; armes : dé de dégâts (`damageDice`).
- **Rareté et objets magiques** : enum `Rarity` DnD5e (COMMON→ARTIFACT) sur `ItemTemplate`, colorée en ANSI dans tous les messages telnet affichant un item (prise, dépôt, équipement, inventaire, description de room, butin, boutique PNJ). Bonus magique `+1`/`+2`/`+3` (champ `bonus`) appliqué au jet d'attaque et aux dégâts de l'arme équipée (`CombatService`) ainsi qu'à la CA de l'armure/du bouclier équipé (`GamePlayer.getArmorClass`) — pas de règle d'attunement (limite du nombre d'objets magiques utilisables simultanément), absente comme le reste des états/conditions.
- **Consommables** : commande `use <item>`, `ConsumableItem`/`ConsumableEffect` (comportement porté par l'item lui-même, pas par un Service externe). 4 paliers de potion de soin DnD5e (soin, supérieure, majeure, suprême — dés et prix RAW), utilisable hors combat (effet immédiat) ou en combat (coûte le tour, `CombatEngine.useItem`). Seul effet implémenté à ce jour : `HEALING` — le point d'extension existe déjà pour un futur poison/buff.
- **Combat** : verbe `attack` (`controller/ingame/Attack.java`, réutilise une cible déjà `select`-ée ou en prend une nouvelle), `game/CombatService.java` (jet d'attaque 1d20+STR+maîtrise+bonus magique vs CA, échec critique sur 1 / réussite critique sur 20, dégâts d'arme ou à mains nues, jet d'initiative 1d20+DEX), `game/CombatEngine.java` (orchestration : fusion des combattants dans un même affrontement, verrou de tour, cascade des tours monstres consécutifs). Classe d'Armure réelle : `10 + mod DEX`, cappée selon la catégorie d'armure équipée, + bonus de bouclier (main secondaire) + bonus magique. Ordre d'initiative DnD5e correct avec insertion de retardataires (`domain/actor/CombatEncounter.java`). Mort/respawn : le joueur réapparaît à PV pleins dans la salle de départ ; un monstre tué est retiré définitivement de sa salle, crédite l'XP au tueur et déclenche son butin (or garanti + objets à tirage indépendant par entrée, `game/actor/LootService.java`, transactionnel).
- **Monstres** : `MonsterInstance` (sous-type scellé de `AbstractCharacter`, distinct du joueur), gabarits + spawns fixes chargés au démarrage (`game/actor/MonsterService.java` + `data/monsters.json`, 7 gabarits). Ripostent avec leur propre initiative une fois attaqués. Ont une zone de présence (`getPresenceRadius`) qui déclenche le combat dès qu'un joueur y entre, sans commande `attack` (`GamePlayerEnteredCell` → `CombatEngine.startAggroEncounter`) — de l'agressivité simple existe donc déjà. Ce qui manque encore : aucun déplacement/patrouille, pas de poursuite d'un joueur qui fuit, pas de décision tactique au-delà de la riposte.
- **PNJ marchands** : `NpcSellerInstance` (sous-type de `AbstractNpc`), catalogue défini dans `data/npcs.json` et dénormalisé contre les templates d'objets au démarrage. `talk` ouvre un dialogue à options (`NpcDialogue` : accueil + liste de réponses), l'option boutique résout un achat réel (`GameNpcSeller.sell` → `GamePlayer.buyItem`) qui débite l'or et ajoute l'objet à l'inventaire (événements `ItemPurchased`/`CharacterSpentGold`, persistés). Aucune vente inverse (le joueur ne peut pas revendre un objet à un PNJ).
- **Or** (complément) : varie donc déjà en jeu par deux voies — butin de monstre (`LootService.receiveGold`) et achat en boutique (`GamePlayer.buyItem`/`trySpendGold`) — au-delà du montant figé à la création mentionné plus haut.
- **Social minimal** : `say` (chat de salle), `stats`/`examine` (affichage HP + caractéristiques, modificateurs, maîtrise).
- **Utilitaire** : lanceur de dés générique `roll XdY+Z` (`game/dice/`), réutilisé pour la génération de personnage et les jets de combat.
- **Modèle de concurrence** : aucun `@Scheduled` nulle part — la boucle de jeu est 100% réactive aux commandes joueur, pas de tick de fond.
- **Tests** : couverture large sur `game`/`persistence`/`domain` (dont `CombatServiceTest`, `CombatEngineTest`, `CombatEncounterTest`, `GameMonsterTest`, `GamePlayerDeathTest`, `MonsterServiceTest`, `CharacterServiceTest`, `LevelServiceTest`), plus un premier test au niveau `controller/**` (`ControllerDispatcherTest`, verrouillage des verbes autorisés pendant un combat).

## Systèmes absents ou à l'état de simple champ de données

### 1. Combat — raffinements restants
Le socle (jets d'attaque, CA, dégâts, initiative, multi-combattants, mort/respawn, butin, agressivité de zone) est fait. Ce qui manque encore :
- **PvP** : absent — `Attack.java` ne résout que des cibles `MonsterInstance`.
- **Respawn de monstre** : aucun — les spawns sont fixes et chargés une seule fois au démarrage.
- **IA de déplacement** : les monstres ne quittent jamais leur salle — pas de patrouille, pas de poursuite d'un joueur qui fuit hors de leur zone de présence, aucune décision tactique au-delà de riposter/rejoindre un affrontement déjà engagé.
- **Attunement d'objets magiques** : aucune limite au nombre d'objets magiques utilisables simultanément (règle DnD5e RAW pour les objets rare+ hors +X armes/armures/boucliers) — non bloquant tant que le nombre d'emplacements d'équipement (6) reste la seule contrainte, mais à revoir si des objets magiques non-équipement (anneaux, bâtons...) sont ajoutés.
- **Contrainte de Force sur l'armure** : pas de seuil de FOR minimum pour les armures moyennes/lourdes ni de désavantage Discrétion associé (règle DnD5e RAW) — `ArmorCategory` ne porte que la CA de base et le plafond de modificateur DEX.

### 2. PNJ / contenu de monde
`AbstractNpc`/`NpcSellerInstance` existent : nom + salle + description (`data/npcs.json`, visible via `examine`), dialogue à options (`talk`, accueil + réponses fixes) et boutique fonctionnelle pour les PNJ marchands (voir « ce qui existe déjà »). Ce qui manque encore : dialogue à état/embranchement (les options sont une liste plate, rejouée à l'identique à chaque `talk`, sans mémoire de ce qui a déjà été dit), quêtes, factions, alignement, PNJ non-marchands avec un rôle actif (garde, soigneur...).

### 3. Sorts / lancer de sorts
Totalement absent (pas de table, pas d'emplacement de sort, pas de composant verbal/somatique/matériel, pas de commande `cast`). Dépend des proficiencies de classe (désormais disponibles, voir « ce qui existe déjà ») pour savoir quels sorts une classe peut accéder. `ConsumableItem`/`ConsumableEffect` (voir « ce qui existe déjà ») esquisse déjà le pattern d'effet à instantané appliqué à un `CharacterInstance` — probablement réutilisable pour un sort à cible unique et effet immédiat, mais rien n'existe encore pour portée/zone d'effet/durée/concentration.

### 4. États (conditions)
Absents (empoisonné, étourdi, paralysé, charmé, effrayé...). Le combat sur lequel ils s'appliqueraient existe désormais — ce système est débloqué et prêt à construire.

### 5. Repos (courte/longue pause)
Absent. Les PV se restaurent déjà via la mort/respawn ou une potion de soin (`use`) ; l'intérêt principal d'un repos sera de restaurer les futurs emplacements de sorts (§3), donc à construire après eux.

### 6. Économie
- **Monnaie** : gagnée (butin de monstre) et dépensée (achat en boutique PNJ) — voir « ce qui existe déjà ». Ce qui manque : revendre un objet à un PNJ (achat uniquement, pas de vente inverse), et tout échange d'or/objet entre joueurs.
- **Encombrement** : `item_template.weight` est stocké et lisible (`ItemTemplate.weight`, `Item.getWeight()`) mais rien ne fait la somme ni ne la compare à la Force — champ mort aujourd'hui.

### 7. Commandes sociales/admin manquantes
`who`, `tell`/`whisper`, `emote`, `help`, `give` (transfert d'objet entre joueurs), commandes de modération/wizard : aucune n'existe (`say` existe déjà). Indépendantes du reste, faisables à tout moment via `/add-command`.

## Lacune transverse notée en passant

`ControllerDispatcherTest` couvre le dispatcher (verrouillage des verbes pendant un combat) ; `Login`, `CharacterCreate`, `Take`, `Equip`, `Roll`, `Attack`, `Check`, `Save`, `Use` ont désormais chacun leur test dédié (voir item 5 de la dette technique ci-dessous). Les autres handlers (`Quit`, `Register`, `CharacterDelete`, `CharacterList`, `CharacterSelect`, `Drop`, `Examine`, `Go`, `Inventory`, `Look`, `Say`, `Select`, `Stats`, `Talk`, `Unequip`) restent sans test dédié — à garder en tête avant d'empiler de nouvelles commandes par-dessus une couche encore partiellement testée individuellement.

## Ordre de construction suggéré

1. Raffinements combat/monstres restants : PvP, respawn de monstre, IA de déplacement
2. États (conditions) — s'appuie sur le combat déjà existant
3. Sorts — dépend des proficiencies de classe (désormais disponibles) et bénéficie des états (étape 2) pour ses effets
4. Repos — dépend des sorts (emplacements à restaurer)
5. Économie restante : vente inverse à un PNJ, échange entre joueurs, encombrement (utilise `weight` déjà stocké)
6. Contenu de monde : dialogue à état/embranchement, quêtes, factions, alignement, PNJ non-marchands actifs
7. Commandes sociales/admin (`who`, `tell`, `emote`, `help`, `give`) — indépendant, à intercaler n'importe quand

## Dette technique et pistes d'amélioration (audit du 2026-08-08)

Section distincte de la feuille de route des systèmes DnD5e ci-dessus : ce qui suit est un audit technique/infra (sécurité, observabilité, cohérence des données, tests, qualité de code), pas des fonctionnalités de jeu. Constats issus d'une passe complète du projet, sans implémentation à ce stade.

### Sécurité — impact haut, effort faible à moyen
- ~~**Fuite de mot de passe en clair dans les logs**~~ **[Résolu]** : `TelnetConnection.handleLine` (`telnet/TelnetConnection.java:46-65`) logge la ligne brute (`log.error("telnet.command.failed line={}", rawLine, e)`) sur toute exception ; la saisie du mot de passe passe par le même mécanisme `pendingLine`/`requestBlocking`, donc une erreur pendant login/register logge le mot de passe en clair au niveau ERROR. Corrigé en réutilisant le signal `SecureOutputMessage` pour rediger la ligne (`[REDACTED]`) quand elle correspond à une saisie secrète.
- ~~`Account.toString()` (`domain/Account.java:71-74`) expose le hash BCrypt — piège pour un futur log de débogage.~~ **[Résolu]** : `password` retiré de `toString()`.
- Aucune protection brute-force sur le login (`controller/connected/Login.java:70-77`) : ni compteur de tentatives, ni délai, ni verrouillage.
- Pas de filtrage des caractères ANSI/contrôle dans les noms de personnage (`CharacterCreate.java`, seulement `.trim()`) ni dans le chat (`Say.java`, `Talk.java`) — injection possible dans le terminal des autres joueurs.
- Telnet en clair, sans TLS — acceptable en dev local, bloquant si le port est un jour exposé au-delà du loopback.
- Identifiants DB en dur et identiques dans `application.yml` et `docker-compose.yml`, aucun profil prod/indirection par variable d'environnement.

### Observabilité — trou structurel
- ~~Seuls 2 fichiers dans tout `src/main` utilisent SLF4J (`telnet/TelnetConnection.java`, `telnet/TelnetServer.java`). Tous les `@EventListener` qui écrivent en DB (`ItemService`, `RoomService`, `CharacterListener`, `LootListener`, `CombatEngine`) n'ont aucun log — aucune trace de login réussi, résolution de combat, ou transaction d'objets/or en dehors du catch-all générique.~~ **[Résolu]** : couverture SLF4J étendue au-delà des `@EventListener` (déjà couverts depuis `e113929`) à la couche contrôleur/telnet/démarrage. Ajouté : `ControllerDispatcher` (`combat.action_blocked`/`command.unknown` en DEBUG), contexte de verbe sur le catch-all `TelnetConnection.handleLine` (`telnet.command.failed verb=...`), durcissement de `TelnetConnection.handleClose` (deux try/catch indépendants, seul chemin du projet auparavant sans aucune protection), cycle de vie de connexion dans `TelnetSessionHandler`, `GameWorld.createCharacter`/`exitWorld`, échecs de login en `WARN` (`Login`), inscription/déconnexion/suppression de personnage (`Register`, `Logout`, `CharacterDelete`), déplacement bloqué (`Go`), et la séquence de warm-up au démarrage (`ServerApplication` + compteurs dans `RoomService`/`ItemService`/`MonsterService`/`NpcService`). Périmètre volontairement restreint côté contrôleurs : seuls les points sans aucun signal ailleurs sont couverts, pas les 27 handlers (la plupart des succès sont déjà tracés en aval par les `@EventListener`, et leurs rejets sont de la friction de gameplay normale sans valeur de diagnostic).
- ~~Aucun endpoint actuator (`/health`, `/metrics`, `/info` absents de `pom.xml`) — aucun signal pour un opérateur hormis la ligne de démarrage.~~ **[Résolu]** : `spring-boot-starter-actuator` + `spring-boot-starter-web` ajoutés (premier serveur HTTP du projet, dédié à Actuator), exposés sur `management.server.port` séparé (8081) via `management.endpoints.web.exposure.include: health,info,metrics` — seuls ces trois endpoints sont accessibles, le reste (`/env`, `/beans`...) répond 404.
- ~~Pas de logging structuré (pas de `logback-spring.xml`), config console par défaut.~~ **[Résolu]** : contexte de corrélation MDC (`connectionId`/`account`/`character`) posé automatiquement pour toute la durée d'une connexion (`TelnetSessionHandler`, `AuthWorld`, `GameWorld`), additif aux logs existants sans y toucher. Configuré entièrement via `application.yml` (`logging.pattern.console`, `logging.file.name`, `logging.logback.rollingpolicy.*`, `logging.structured.format.file`), sans `logback-spring.xml` ni dépendance supplémentaire — le support « structured logging » natif de Spring Boot 4 suffit : console lisible enrichie de `%mdc`, fichier rotatif en JSON (`logstash`).

### Cohérence des données / transactions
- ~~`LootService.onCharacterDied` (`game/actor/LootService.java:44-60`) enchaîne un `receiveGold` puis une boucle de `receiveLootItem` par entrée de butin, chacun déclenchant une écriture DB synchrone séparée et non transactionnelle (seul `ItemService.onGamePlayerEquippedItem` est `@Transactional` dans tout le projet) — un crash en cours de boucle laisse un butin partiellement persisté.~~ **[Résolu]** : `@Transactional` ajouté sur `LootService.onCharacterDied`. La dispatch d'événements étant synchrone et sur le même thread, `CharacterService.onCharacterReceivedGold` (`characterDao.update`) et `ItemService.onCharacterLootedItem` (`itemDao.insert`) rejoignent cette transaction via le gestionnaire Spring/jOOQ lié au thread courant, sans avoir besoin d'être `@Transactional` eux-mêmes.
- ~~`V1__init_schema.sql` : aucun index au-delà des clés primaires/`uniq_character_slot` — pas d'index sur `character.account_id`, `item.character_id`/`room_id`/`template_id`, colonnes pourtant utilisées par `findByAccountId`/`findByRoomId`/`findByCharacterId` — scan séquentiel garanti à l'échelle.~~ **[Résolu]** : `V4__add_fk_indexes.sql` ajoute `idx_character_account_id`, `idx_item_character_id`, `idx_item_room_id`, `idx_item_template_id`.
- Aucune contrainte `CHECK` (PV ≥ 0, or ≥ 0, xp ≥ 0, niveau ≥ 1) — un bug applicatif pourrait persister silencieusement des valeurs négatives.
- `application.yml` ne définit aucun `spring.datasource.hikari.*` → pool HikariCP par défaut (10 connexions) ; comme chaque action joueur peut déclencher une écriture JDBC synchrone depuis un `@EventListener`, ce pool par défaut est un goulot d'étranglement potentiel avant même les limites CPU.

### Concurrence — au-delà du pickup d'objets déjà traité
- Le TOCTOU de double-login déjà connu (`Login.java:76-79`) a un rayon d'impact plus large que prévu : `GamePlayer.equipItem` documente explicitement (javadoc, `GamePlayer.java:376-379`) que l'invariant « une connexion pilote un seul personnage » n'est pas garanti. S'il est un jour déclenché, deviennent réellement concurrents et non protégés : `PlayerInventory.addGold`/`trySpendGold` (`+=` non atomique, `PlayerInventory.java:29-42`), la liste d'inventaire, et `GamePlayer.takeDamage` (protégé seulement par le lock d'engagement du `CombatEngine`). À l'inverse, `Room.tryClaimCell`/`join`/`leave` et `GameMonster.takeDamage` sont déjà correctement verrouillés.

### Tests — zones non couvertes
- ~~Couche telnet entièrement non testée : `TelnetSessionHandler`, `TelnetConnection`, `TelnetServer`, `IacFilterDecoder`, `TelnetServerInitializer`.~~ **[Résolu]** : voir item 5 de la priorisation ci-dessous.
- ~~`LootListener` n'a aucun test — la logique de probabilité de butin n'est pas vérifiée.~~ **[Résolu]** : voir item 5 de la priorisation ci-dessous.
- Cf. aussi la lacune transverse déjà notée plus haut sur la couverture par `ControllerHandler` — les handlers les plus utilisés sont désormais couverts (item 5 ci-dessous), le reste ne l'est pas encore.

### Qualité de code / duplication — effort faible
- Logique de normalisation + parsing d'enum dupliquée à l'identique 3 fois : `Save.java:63-77`, `Check.java:63-77`, `CharacterCreate.java:101,129,157` (`strip().toLowerCase().replace(' ','_').replace('-','_')` + `valueOf` avec try/catch) — bon candidat pour un utilitaire partagé.
- `GamePlayer.java` (468 lignes, 40 membres publics) et `CombatEngine.java` (394 lignes) approchent la taille de god-class vu leur centralité.
- `ItemService.warmRoomItems` (`ItemService.java:123-129`) fait un appel DAO par salle au warm-up (N+1) plutôt qu'un fetch groupé — impact faible car limité au démarrage.
- Pas de validation de longueur max sur les noms de compte/personnage, seulement la limite de ligne telnet à 1024 caractères.
- Pas de pipeline CI (`.github/workflows` absent) : spotless et les tests ne tournent qu'en local, rien ne les impose avant merge.

### Priorisation suggérée
1. ~~Sécurité rapide : ne plus logger `rawLine` tel quel pendant la saisie de mot de passe, retirer `password` du `toString()` de `Account`.~~ **[Résolu]**
2. ~~Logging applicatif dans les `@EventListener` de `game/*Service` — préalable à tout diagnostic futur.~~ **[Résolu]** : SLF4J ajouté à `ItemService`/`RoomService`/`CharacterListener`/`LootListener`/`CombatEngine` (transactions d'objets/or, mort/respawn, montée de niveau, butin, join/leave d'encounter — `INFO`, déplacement de room et nettoyage d'encounter en `DEBUG`), ainsi qu'à la résolution de combat proprement dite dans `CombatEngine` (`attack`/`performTurnAttack`/`resolveFromCurrentTurn`, hors périmètre strict des `@EventListener` mais nécessaire pour que le combat soit réellement traçable) et au login réussi (`Login.onPasswordEntered`).
3. ~~Transaction sur `LootService.onCharacterDied`.~~ **[Résolu]** : `@Transactional` ajouté sur `LootService.onCharacterDied` — grâce à la propagation par thread du gestionnaire de transaction Spring (même mécanisme que `ItemService.onGamePlayerEquippedItem`), la transaction ouverte ici englobe aussi les écritures faites par les `@EventListener` déclenchés en aval (`CharacterService.onCharacterReceivedGold` → `characterDao.update`, `ItemService.onCharacterLootedItem` → `itemDao.insert`), sans changement dans ces classes.
4. ~~Index DB sur les colonnes FK (`account_id`, `character_id`, `room_id`, `template_id`).~~ **[Résolu]** : `V4__add_fk_indexes.sql`.
5. ~~Tests : couche telnet, `LootListener`, puis les `ControllerHandler` les plus utilisés.~~ **[Résolu]** : `IacFilterDecoderTest`, `TelnetConnectionTest`, `TelnetSessionHandlerTest`, `TelnetServerInitializerTest` (couche telnet, via `EmbeddedChannel` et des doublons de test par sous-classement — pas de Mockito dans ce projet — `TelnetServer` volontairement laissé hors périmètre car `start()` bloque sur un vrai bootstrap réseau Netty, non testable unitairement ; nécessiterait un test d'intégration dédié à part) ; `LootServiceTest` (or garanti, drop 100 %/0 % indépendant par entrée, table vide, or + butin dans la même transaction) ; `LoginTest`, `CharacterCreateTest`, `TakeTest`, `EquipTest`, `RollTest`, `AttackTest`, `CheckTest`, `SaveTest` (couverture des `ControllerHandler` les plus utilisés, via un `RecordingConnection` désormais mutualisé sous `controller/RecordingConnection.java`, avec callback de prompt pour piloter les scénarios multi-étapes de `Login`/`CharacterCreate`).
6. Pipeline CI minimal (build + test + spotless check).
7. Config secrets/profils avant tout déploiement réel (hors scope tant que ça reste du dev local).
8. Refactor de la logique normalize+parse dupliquée.
9. Reconsidérer le TOCTOU de double-login à la lumière de son vrai rayon d'impact (or/inventaire/HP), pas seulement comme un doublon de session.

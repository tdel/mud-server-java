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
- **Or** : `PlayerInventory.gold`, tiré à la création selon la classe (`GameWorld.createCharacter`), persisté (`V3__add_gold.sql`), affiché uniquement via `inventory` (volontairement absent de `stats`/`examine`). Immuable après création — aucune boutique ni butin ne le fait varier.
- **Points de vie réels** : PV max au niveau 1 = `hitDie` + mod CON (plus figés à 100/100). `currentHealth` est modifié par les dégâts de combat, la mort/respawn et la montée de niveau.
- **Monde** : `Room`/`RoomExit` en cache mémoire chaud, navigation (`go`), description (`look`), broadcast d'arrivée/départ.
- **Objets** : `ItemTemplate`/`Item`, 10 types, 6 slots d'équipement, take/drop/equip/unequip avec gestion de concurrence (`synchronized(item)`, événements de domaine + projection DB). Armures : catégorie (léger/moyen/lourd) + CA de base ; armes : dé de dégâts (`damageDice`).
- **Combat** : verbe `attack` (`controller/ingame/Attack.java`, réutilise une cible déjà `select`-ée ou en prend une nouvelle), `game/CombatService.java` (jet d'attaque 1d20+STR+maîtrise vs CA, échec critique sur 1 / réussite critique sur 20, dégâts d'arme ou à mains nues, jet d'initiative 1d20+DEX), `game/CombatEngine.java` (orchestration : fusion des combattants dans un même affrontement, verrou de tour, cascade des tours monstres consécutifs). Classe d'Armure réelle : `10 + mod DEX`, cappée selon la catégorie d'armure équipée, + bonus de bouclier (main secondaire). Ordre d'initiative DnD5e correct avec insertion de retardataires (`domain/actor/CombatEncounter.java`). Mort/respawn : le joueur réapparaît à PV pleins dans la salle de départ ; un monstre tué est retiré définitivement de sa salle et crédite l'XP au tueur.
- **Monstres** : `GameMonster` (sous-type scellé de `GameCharacter`, distinct du joueur), gabarits + spawns fixes chargés au démarrage (`game/actor/MonsterService.java` + `data/monsters.json`, 7 gabarits). Ripostent avec leur propre initiative une fois attaqués, mais n'ont aucune IA/agressivité propre (n'initient jamais un combat, ne se déplacent pas).
- **Social minimal** : `say` (chat de salle), `stats`/`examine` (affichage HP + caractéristiques, modificateurs, maîtrise).
- **Utilitaire** : lanceur de dés générique `roll XdY+Z` (`game/dice/`), réutilisé pour la génération de personnage et les jets de combat.
- **Modèle de concurrence** : aucun `@Scheduled` nulle part — la boucle de jeu est 100% réactive aux commandes joueur, pas de tick de fond.
- **Tests** : couverture large sur `game`/`persistence`/`domain` (dont `CombatServiceTest`, `CombatEngineTest`, `CombatEncounterTest`, `GameMonsterTest`, `GamePlayerDeathTest`, `MonsterServiceTest`, `CharacterServiceTest`, `LevelServiceTest`), plus un premier test au niveau `controller/**` (`ControllerDispatcherTest`, verrouillage des verbes autorisés pendant un combat).

## Systèmes absents ou à l'état de simple champ de données

### 1. Combat — raffinements restants
Le socle (jets d'attaque, CA, dégâts, initiative, multi-combattants, mort/respawn) est fait. Ce qui manque encore :
- **PvP** : absent — `Attack.java` ne résout que des cibles `GameMonster`.
- **Butin** : aucun drop à la mort d'un monstre, il est simplement retiré de la salle.
- **Respawn de monstre** : aucun — les spawns sont fixes et chargés une seule fois au démarrage.
- **IA/agressivité** : les monstres ne font que riposter une fois attaqués ; pas de déplacement, pas d'initiative d'attaque de leur part.

### 2. PNJ / contenu de monde
`GameNpc` existe (nom + salle, `data/npcs.json`, visible via `examine`) mais reste décoratif : pas de dialogue, pas de quêtes, pas de factions, pas d'alignement.

### 3. Sorts / lancer de sorts
Totalement absent (pas de table, pas d'emplacement de sort, pas de composant verbal/somatique/matériel, pas de commande `cast`). Dépend des proficiencies de classe (désormais disponibles, voir « ce qui existe déjà ») pour savoir quels sorts une classe peut accéder.

### 4. États (conditions)
Absents (empoisonné, étourdi, paralysé, charmé, effrayé...). Le combat sur lequel ils s'appliqueraient existe désormais — ce système est débloqué et prêt à construire.

### 5. Repos (courte/longue pause)
Absent. Les PV se restaurent déjà via la mort/respawn ; l'intérêt principal d'un repos sera de restaurer les futurs emplacements de sorts (§3), donc à construire après eux.

### 6. Économie
- **Monnaie** : l'or existe mais est figé à la création — aucune mécanique ne le fait gagner ou dépenser.
- **Encombrement** : `item_template.weight` est stocké et lisible (`ItemTemplate.weight`, `Item.getWeight()`) mais rien ne fait la somme ni ne la compare à la Force — champ mort aujourd'hui.
- **Boutiques/marchands** : absents.

### 7. Commandes sociales/admin manquantes
`who`, `tell`/`whisper`, `emote`, `help`, `give` (transfert d'objet entre joueurs), commandes de modération/wizard : aucune n'existe (`say` existe déjà). Indépendantes du reste, faisables à tout moment via `/add-command`.

## Lacune transverse notée en passant

Un premier test existe désormais au niveau `controller/**` (`ControllerDispatcherTest`), mais il couvre le dispatcher (verrouillage des verbes pendant un combat), pas les handlers un par un — aucun test dédié pour `Login`, `CharacterCreate`, `Take`, `Equip`, `Roll`, `Attack`, `Check`, `Save`, etc. À garder en tête avant d'empiler de nouvelles commandes par-dessus une couche encore peu testée individuellement.

## Ordre de construction suggéré

1. Raffinements combat/monstres : PvP, butin, respawn de monstre, agressivité simple
2. États (conditions) — s'appuie sur le combat déjà existant
3. Sorts — dépend des proficiencies de classe (désormais disponibles) et bénéficie des états (étape 2) pour ses effets
4. Repos — dépend des sorts (emplacements à restaurer)
5. Économie : boutiques, dépense d'or, encombrement (utilise `weight` déjà stocké)
6. Contenu de monde : PNJ interactifs, dialogues, quêtes, factions, alignement
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
- Seuls 2 fichiers dans tout `src/main` utilisent SLF4J (`telnet/TelnetConnection.java`, `telnet/TelnetServer.java`). Tous les `@EventListener` qui écrivent en DB (`ItemService`, `RoomService`, `CharacterService`, `LootService`, `CombatEngine`) n'ont aucun log — aucune trace de login réussi, résolution de combat, ou transaction d'objets/or en dehors du catch-all générique.
- ~~Aucun endpoint actuator (`/health`, `/metrics`, `/info` absents de `pom.xml`) — aucun signal pour un opérateur hormis la ligne de démarrage.~~ **[Résolu]** : `spring-boot-starter-actuator` + `spring-boot-starter-web` ajoutés (premier serveur HTTP du projet, dédié à Actuator), exposés sur `management.server.port` séparé (8081) via `management.endpoints.web.exposure.include: health,info,metrics` — seuls ces trois endpoints sont accessibles, le reste (`/env`, `/beans`...) répond 404.
- Pas de logging structuré (pas de `logback-spring.xml`), config console par défaut.

### Cohérence des données / transactions
- `LootService.onCharacterDied` (`game/actor/LootService.java:44-60`) enchaîne un `receiveGold` puis une boucle de `receiveLootItem` par entrée de butin, chacun déclenchant une écriture DB synchrone séparée et non transactionnelle (seul `ItemService.onGamePlayerEquippedItem` est `@Transactional` dans tout le projet) — un crash en cours de boucle laisse un butin partiellement persisté.
- `V1__init_schema.sql` : aucun index au-delà des clés primaires/`uniq_character_slot` — pas d'index sur `character.account_id`, `item.character_id`/`room_id`/`template_id`, colonnes pourtant utilisées par `findByAccountId`/`findByRoomId`/`findByCharacterId` — scan séquentiel garanti à l'échelle.
- Aucune contrainte `CHECK` (PV ≥ 0, or ≥ 0, xp ≥ 0, niveau ≥ 1) — un bug applicatif pourrait persister silencieusement des valeurs négatives.
- `application.yml` ne définit aucun `spring.datasource.hikari.*` → pool HikariCP par défaut (10 connexions) ; comme chaque action joueur peut déclencher une écriture JDBC synchrone depuis un `@EventListener`, ce pool par défaut est un goulot d'étranglement potentiel avant même les limites CPU.

### Concurrence — au-delà du pickup d'objets déjà traité
- Le TOCTOU de double-login déjà connu (`Login.java:76-79`) a un rayon d'impact plus large que prévu : `GamePlayer.equipItem` documente explicitement (javadoc, `GamePlayer.java:376-379`) que l'invariant « une connexion pilote un seul personnage » n'est pas garanti. S'il est un jour déclenché, deviennent réellement concurrents et non protégés : `PlayerInventory.addGold`/`trySpendGold` (`+=` non atomique, `PlayerInventory.java:29-42`), la liste d'inventaire, et `GamePlayer.takeDamage` (protégé seulement par le lock d'engagement du `CombatEngine`). À l'inverse, `Room.tryClaimCell`/`join`/`leave` et `GameMonster.takeDamage` sont déjà correctement verrouillés.

### Tests — zones non couvertes
- Couche telnet entièrement non testée : `TelnetSessionHandler`, `TelnetConnection`, `TelnetServer`, `IacFilterDecoder`, `TelnetServerInitializer`.
- `LootService` n'a aucun test (constat nouveau, pas encore listé dans la lacune transverse ci-dessus) — la logique de probabilité de butin n'est pas vérifiée.
- Cf. aussi la lacune transverse déjà notée plus haut sur la couverture par `ControllerHandler`.

### Qualité de code / duplication — effort faible
- Logique de normalisation + parsing d'enum dupliquée à l'identique 3 fois : `Save.java:63-77`, `Check.java:63-77`, `CharacterCreate.java:101,129,157` (`strip().toLowerCase().replace(' ','_').replace('-','_')` + `valueOf` avec try/catch) — bon candidat pour un utilitaire partagé.
- `GamePlayer.java` (468 lignes, 40 membres publics) et `CombatEngine.java` (394 lignes) approchent la taille de god-class vu leur centralité.
- `ItemService.warmRoomItems` (`ItemService.java:123-129`) fait un appel DAO par salle au warm-up (N+1) plutôt qu'un fetch groupé — impact faible car limité au démarrage.
- Pas de validation de longueur max sur les noms de compte/personnage, seulement la limite de ligne telnet à 1024 caractères.
- Pas de pipeline CI (`.github/workflows` absent) : spotless et les tests ne tournent qu'en local, rien ne les impose avant merge.

### Priorisation suggérée
1. ~~Sécurité rapide : ne plus logger `rawLine` tel quel pendant la saisie de mot de passe, retirer `password` du `toString()` de `Account`.~~ **[Résolu]**
2. Logging applicatif dans les `@EventListener` de `game/*Service` — préalable à tout diagnostic futur.
3. Transaction sur `LootService.onCharacterDied`.
4. Index DB sur les colonnes FK (`account_id`, `character_id`, `room_id`, `template_id`).
5. Tests : couche telnet, `LootService`, puis les `ControllerHandler` les plus utilisés.
6. Pipeline CI minimal (build + test + spotless check).
7. Config secrets/profils avant tout déploiement réel (hors scope tant que ça reste du dev local).
8. Refactor de la logique normalize+parse dupliquée.
9. Reconsidérer le TOCTOU de double-login à la lumière de son vrai rayon d'impact (or/inventaire/HP), pas seulement comme un doublon de session.

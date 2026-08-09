# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project
This project is a game. It is built around DnD5e (Dungeon and Dragon 5th edition). Every system in this game must be compliant with DnD5e.

## DnD5e rules reference

`docs/dnd5e/` holds a scoped, per-system summary of the DnD5e SRD rules (sourced from the official CC-BY-licensed SRD 5.1), each file ending with a "Notes for this project" section tying the rule to the current implementation. Load only the file(s) relevant to the system being touched — don't read the whole directory speculatively. Start from `docs/dnd5e/README.md` for the full index; direct links to the most commonly relevant files:

- [Ability scores, checks & saves](docs/dnd5e/ability-scores.md)
- [Races](docs/dnd5e/races.md) / [Classes](docs/dnd5e/classes.md) / [Backgrounds & alignment](docs/dnd5e/backgrounds-alignment.md)
- [Leveling & XP](docs/dnd5e/leveling-xp.md)
- [Combat](docs/dnd5e/combat.md) / [Conditions](docs/dnd5e/conditions.md) / [Damage, healing & death](docs/dnd5e/damage-healing-death.md)
- [Resting](docs/dnd5e/resting.md) / [Movement & environment](docs/dnd5e/movement-environment.md)
- [Equipment](docs/dnd5e/equipment.md) / [Magic items](docs/dnd5e/magic-items.md) (rarity + bonus system already implemented — see notes there)
- [Spellcasting](docs/dnd5e/spellcasting.md) (not yet implemented) / [Feats](docs/dnd5e/feats.md) (not yet implemented)
- [Monsters & NPCs](docs/dnd5e/monsters-npcs.md)

## Build, test, run

Neither Java nor Maven is installed on the host — Maven runs through Docker instead, using the locally-pulled `maven:3.9.16-eclipse-temurin-25` image:

```
docker run --rm -v "$(pwd)":/app -w /app -v /var/run/docker.sock:/var/run/docker.sock -v ~/.m2:/root/.m2 maven:3.9.16-eclipse-temurin-25 mvn <goal>
```

The Docker socket mount is required even for plain `mvn test` — it's not just for the app's own Postgres, it's how Testcontainers launches its own sibling Postgres container from inside the Maven container. Substitute `<goal>` with:

- `package` — build
- `spring-boot:run` (or run the packaged jar directly) — run the app
- `test` — run tests. No unit/integration split exists; every run spins up a Testcontainers Postgres, so Docker access is always required, not optional.
- `spotless:apply` / `spotless:check` — format / verify formatting. `check` also runs automatically in the `verify` phase (not `test`). Uses the Eclipse formatter, not google-java-format — google-java-format's javac-internals dependency is currently broken on Java 25 ([diffplug/spotless#2468](https://github.com/diffplug/spotless/issues/2468)).

Separately, `docker-compose.yml` at the repo root runs a persistent Postgres for local dev (not for tests): host port **5433** → container 5432 (db `mud-server-java`). The telnet server itself listens on a different port, **4001** (`app.telnet.port` in `application.yml`) — don't confuse the two. A third port, **8081** (`management.server.port`), serves Spring Boot Actuator (`/actuator/health`, `/actuator/info`, `/actuator/metrics` only — see `management.endpoints.web.exposure.include`); it's the only HTTP surface in the project, added solely to carry Actuator since the game protocol itself stays raw telnet.

## Stack specifics

- Java 25, Spring Boot 4.1.0. Persistence is jOOQ (`DSLContext`, `spring-boot-starter-jooq`) — a type-safe SQL builder, not an ORM: no persistence-context, no lazy loading, no dirty-checking. Chosen over JPA/Hibernate specifically because DAO call sites never run inside a transaction except `ItemService` (see below), which would fight an ORM's session/entity-lifecycle assumptions.
- The DSL classes (`persistence.jooq.tables.*`, `persistence.jooq.tables.records.*`) are generated at build time (`generate-sources` phase, `jooq-codegen-maven`) directly from the Flyway migration files (`V1__init_schema.sql`, `V2__add_xp.sql`) via `org.jooq.meta.extensions.ddl.DDLDatabase` — **no live database connection is needed for codegen**, it parses the Flyway SQL files themselves. Flyway stays the single source of truth for the schema; jOOQ only reflects it. The plugin's `scripts` property is an Ant-style glob (currently `V*.sql`, matched via `org.jooq.FilePattern` — not a comma-separated list), sorted with `sort=semantic` so `V2` is read after `V1`; a new migration file just needs to match the same `V*.sql` pattern to be picked up, no pom.xml change required.
- DAOs (`persistence/*Dao`) keep a stable public API (`insert`/`findById`/etc.) regardless of the underlying query engine — internals use `dsl.selectFrom(TABLE)...fetch(RowMapperEquivalent)` with a small `private static toDomain(...)` mapper per DAO, replacing the old hand-written `RowMapper` classes.
- Boot 4 split several things out of core that older Boot versions bundled: Flyway needs the dedicated `spring-boot-starter-flyway` (plain `flyway-core` isn't enough) plus `flyway-database-postgresql`; Jackson's `ObjectMapper` bean comes from `spring-boot-starter-json`.
- `spring-security-crypto` is used only for BCrypt — not the full Spring Security starter.
- Tests use AssertJ (`assertThat`), not Mockito — the test suite is integration-style against a real Postgres container rather than mocked.
- Telnet is the **current** transport, not a permanent assumption. `network/OutputMessage` is a transport-agnostic marker interface; `telnet/OutputTelnetMessage` (`toTelnet(TelnetOutput)`) is telnet's own rendering contract, implemented by every record under `network/message/**`. Nothing under `domain`/`game` imports `telnet.*`. The long-term plan is a graphical 2D/2.5D client (Godot or Unity, undecided) talking a different protocol; keep new `domain`/`game` code free of telnet-specific concerns so that swap stays contained. The one known friction point: `toTelnet` rendering lives on the message records themselves rather than being isolated inside `telnet/`, so adding a second transport means adding a second `toXxx` method per message record, not just a new package.

## Domain model & events

- Domain objects (`domain/actor/GamePlayer`, `GameMonster`, `GameNpc`/`GameNpcSeller`, `domain/Room`, `domain/Item`) are plain POJOs, never Spring beans, and carry their own business logic rather than being anemic data holders pushed around by Services — e.g. `GamePlayer.pickUpItem/equipItem/unequipItem/dropItem/buyItem/receiveLootItem/receiveGold/gainXp/takeDamage/moveToRoom`, `PlayerInventory.addItem/removeItem/trySpendGold`, `Room.join/leave/broadcast/tryClaimCell`. None of them hold a DAO reference, which is what makes them unit-testable without Spring or Postgres (`GamePlayerTest`, `ItemRaceConditionTest`).
- Because these objects aren't Spring-managed, they can't get a constructor-injected `ApplicationEventPublisher`. `domain/actor/event/DomainEventPublisher` is a static holder around Spring's publisher instead, initialized once at startup by `config/DomainEventPublisherInitializer` — well before any domain mutation is possible (that only happens while handling a telnet command, i.e. after the context is fully up).
- Every business mutation applies the in-memory change first, then calls `DomainEventPublisher.publish(...)` with an event record from `domain/actor/event/*` (one file per event — `ItemPickedUp`, `GamePlayerEquippedItem`, `GamePlayerUnequippedItem`, `CharacterLootedItem`, `ItemPurchased`, `CharacterReceivedGold`/`CharacterSpentGold`, `CharacterGainedXp`, `CharacterDied`/`GamePlayerDied`, `GamePlayerMovedToRoom`/`SpawnedToRoom`/`EnteredCell`, `NewGamePlayerCreated`, etc).
- `@EventListener` methods (in `game/ItemService`, `game/RoomService`, `game/actor/CharacterService`, `game/actor/LootService`, `game/CombatEngine`, `game/GameWorld`) are the *only* places that write to the DB — a Service never contains the business rule itself, only the persistence of a fact the domain object already applied. Dispatch is deliberately synchronous (plain `@EventListener`, no `@Async`), to avoid reopening the race windows the in-memory locking (see below) is designed to close. `@Order` is used sparingly, only where message ordering to the player matters (e.g. death broadcast before the level-up message). Only `ItemService.onGamePlayerEquippedItem` is `@Transactional`, so both `updateSlot` calls it triggers commit together.

## Persistence: Postgres vs classpath JSON

Two kinds of data live in two different places, split by nature (static rules vs. in-play mutable state), not by convenience:

- **Classpath JSON** (`src/main/resources/data/{rooms,items,monsters,npcs,race,class,levels}.json`), loaded once via Jackson `ObjectMapper` during warm-up (see below), never written back, and never represented in the Flyway migrations. This is game-content/rules data: item templates, race/class definitions, the XP table, room topology and monster spawn points.
- **Postgres via jOOQ** (`persistence/*Dao`: `AccountDao`, `CharacterDao`, `ItemDao`): state that changes during play — accounts, a character's position/HP/XP/gold, and item *instance* ownership/equipped slot. Item *templates* are file-based rules data; item *instances* (who holds one, what room, what slot) are DB rows that get a file template attached onto them in memory.
- The two are stitched together by the event pattern above: `@EventListener` methods are the only point where in-memory state (whether it originated from a file or from the DB) gets pushed to Postgres.

## Warm-up & in-memory caches

- `ServerApplication.warmupRunner` (an `ApplicationRunner` bean, conditional on `app.telnet.enabled`) runs after the Spring context refreshes but before `ApplicationReadyEvent`/`TelnetServer.start()`. Call order is significant and documented in its Javadoc: rooms → item templates → monsters → npcs → room items → race → class → levels, each step depending on data loaded by the previous one (e.g. monster loot tables reference item template ids, validated eagerly instead of failing on first drop).
- Each `*Service` (`RoomService`, `ItemService`, `RaceService`, `ClassService`, `LevelService`, `MonsterService`, `NpcService`) holds a `ConcurrentHashMap` populated once at startup and never invalidated or reloaded. A `Room`/`Item` lives as exactly one instance for the whole process lifetime — see the pickup-race discussion below.
- This is a deliberately simple, **single-process** model: there's no invalidation and no cross-instance synchronization if the server were ever split across multiple processes, and the in-memory locking below (`synchronized(item)`) silently depends on staying single-process. Treat this as a known simplification likely to need rework before any horizontal scaling, not as a settled design decision.

## Virtual-thread architecture

- `config/VirtualThreadExecutorConfig` exposes one shared `Executors.newVirtualThreadPerTaskExecutor()` bean. Never block JDBC or other blocking logic on Netty's NIO threads.
- `telnet/TelnetSessionHandler` spawns **exactly one virtual thread per connection**, which loops `inbox.take()`-ing from a `LinkedBlockingQueue<String>` fed by `channelRead0`. Do **not** submit each incoming line independently to the executor — two lines arriving in the same TCP packet could then run on different virtual threads with no ordering guarantee. The single-consumer queue is what preserves per-connection command ordering.
- `domain/actor/GamePlayer.pickUpItem` is the concurrency-critical path for item pickup races: item management lives entirely in memory (one live `Item` instance per row for the process lifetime, via `RoomService`/`ItemService`'s warm caches — never reloaded per request, see above), so the race is resolved with a `synchronized(item)` block rather than a DB-level lock — no more `@Transactional`/`SELECT ... FOR UPDATE` on this path (that pessimistic-lock approach was retired; see git history if you need it). `synchronized` no longer pins virtual threads to their carrier since JEP 491 (JDK 24+), so this doesn't violate the "never block a virtual thread" rule above. `game/ItemRaceConditionTest` proves exactly-one-winner semantics using real concurrent virtual threads + a `CyclicBarrier` calling `GamePlayer.pickUpItem` directly, and is deliberately **not** `@Transactional` at the test level (the DB write triggered by the `ItemPickedUp` domain event shouldn't share the test thread's connection). `domain/actor/GamePlayer.equipItem`/`unequipItem` follow the same domain-event pattern (no DB writes inline) but need no lock at all — a player is only ever driven by its own connection, whose commands run one at a time on a single virtual thread, so no two threads can mutate the same character's inventory concurrently (this silently depends on "one connection per account" actually holding — see the TODO in `controller/connected/Login`, a known pre-existing gap). `equipItem` publishes one `GamePlayerEquippedItem` event carrying both the newly-equipped item and any previous occupant of the same slot, so `game/ItemService.onGamePlayerEquippedItem` (`@EventListener` + `@Transactional`) can apply both `updateSlot` calls in one transaction — matches the deferred `uniq_character_slot` constraint in `V1__init_schema.sql`.

## Test context gotcha

`TelnetServer.start()` blocks the calling thread (`channel.closeFuture().sync()`) — it's the app's real main loop, triggered on `ApplicationReadyEvent`. Any `@SpringBootTest` will hang at context startup unless `app.telnet.enabled=false` resolves in that test context (already set in `src/test/resources/application.yml` — keep it that way for new test config).

## Conventions

- Non-obvious classes get French-language Javadoc explaining *why*, not *what* (often comparing against a prior PHP/Swoole implementation being ported). Follow this style for new non-obvious code.
- Package-by-feature at the top level (`config`, `controller`, `domain`, `game`, `network`, `persistence`, `telnet`), package-by-layer within. Domain entities and outbound network messages are Java `record`s.
- One `ControllerHandler` class per in-game command verb under `controller/{connected,authed,ingame}`, auto-discovered by `ControllerRegistry` (constructor-injected `List<ControllerHandler>`, no manual registration), paired with a response `record` under `network/message/**`. Use `/add-command` to scaffold a new one.
- Commit messages: French, no phase-numbering scheme (earlier history used "Phase N : ..." — don't continue that numbering).

## Recent features not yet reflected above

- Movement was reworked from a classic room/exit graph to a **hexagonal grid** (`domain/HexCoordinate`, `RoomPortal`, `game/HexGridRenderer`); `RoomDescription` renders an ASCII hex viewport instead of a plain exit list.
- Merchant NPCs (`domain/actor/GameNpcSeller`) sell from a shop catalog defined in `data/npcs.json`, denormalized against item templates at warm-up.
- Monsters drop gold and items on death (`game/actor/LootService`, `@Order(3)` on `CharacterDied`).
- Monsters have a presence/aggro zone that triggers combat when a player enters it (`GamePlayerEnteredCell` → `game/CombatEngine`).
- Monster spawn points moved from `monsters.json` into `rooms.json` (`Room.getMonsterSpawns()`) — `monsters.json` now holds only templates and loot tables.

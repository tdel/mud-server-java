# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
Please talk in French, without too much verbosity. Be concise.

## Project
This project is a game. It is built around DnD5e (Dungeon and Dragon 5th edition). Every system in this game must be compliant with DnD5e.

## DnD5e rules reference

`docs/dnd5e/` holds a scoped, per-system summary of the DnD5e SRD rules (sourced from the official CC-BY-licensed SRD 5.1), each file ending with a "Notes for this project" section tying the rule to the current implementation. Load only the file(s) relevant to the system being touched — don't read the whole directory speculatively. Start from `docs/dnd5e/README.md` for the full index; direct links to the most commonly relevant files:

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
- **Two transports** run in parallel: telnet (`network/server/telnet`) and a JSON/TCP transport for a future TUI client (`network/server/tui`). `network/OutputMessage` is a transport-agnostic marker interface; `network/server/telnet/OutputTelnetMessage` (`toTelnet(TelnetOutput)`) and `network/OutputJsonMessage` (`toJson(JsonOutput)`) are each transport's own rendering contract, both implemented by every record under `network/message/**`. Nothing under `domain`/`game` imports `network.server.*`. The long-term plan is also a graphical 2D/2.5D client (Godot or Unity, undecided) talking a different protocol; keep new `domain`/`game` code free of transport-specific concerns so future swaps stay contained. Unlike `toTelnet` (hand-written prose + ANSI per record), `OutputJsonMessage.toJson` has a **default implementation** that serializes the record as-is via Jackson (records serialize natively, no annotations needed) — so most of the 126 records need no method body at all, just `implements ... OutputJsonMessage` in the type header. Only records that embed a domain object instead of plain value fields (e.g. `ViewAround`, `GamePlayerStats`, `MonsterStatBlock`) override `toJson` to build a small nested DTO (`Payload`/`CellView`/etc. record) instead of serializing the domain object graph. `network/server/tui/TuiConnection.send()` still throws if a message doesn't implement `OutputJsonMessage`, same as telnet's strict check — the default method just means that never happens in practice.

## Domain model & events

- Domain objects (`domain/actor/GamePlayer`, `MonsterInstance`, `AbstractNpc`/`NpcSellerInstance`, `domain/Room`, `domain/Item`) are plain POJOs, never Spring beans, and carry their own business logic rather than being anemic data holders pushed around by Services — e.g. `GamePlayer.equipItem/unequipItem/discardItem/buyItem/receiveLootItem/receiveGold/gainXp/takeDamage/moveToRoom`, `PlayerInventory.addItem/removeItem/trySpendGold`, `Room.join/leave/broadcast/tryClaimCell`. None of them hold a DAO reference, which is what makes them unit-testable without Spring or Postgres (`GamePlayerTest`).
- Because these objects aren't Spring-managed, they can't get a constructor-injected `ApplicationEventPublisher`. `domain/actor/event/DomainEventPublisher` is a static holder around Spring's publisher instead, initialized once at startup by `config/DomainEventPublisherInitializer` — well before any domain mutation is possible (that only happens while handling a telnet command, i.e. after the context is fully up).
- Every business mutation applies the in-memory change first, then calls `DomainEventPublisher.publish(...)` with an event record from `domain/actor/event/*` (one file per event — `ItemDiscarded`, `GamePlayerEquippedItem`, `GamePlayerUnequippedItem`, `CharacterLootedItem`, `ItemPurchased`, `CharacterReceivedGold`/`CharacterSpentGold`, `CharacterGainedXp`, `CharacterDied`/`GamePlayerDied`, `GamePlayerMovedToRoom`/`SpawnedToRoom`/`EnteredCell`, `NewGamePlayerCreated`, etc).
- `@EventListener` methods (in `game/ItemService`, `game/WorldInstanceService`, `game/actor/CharacterService`, `game/actor/LootService`, `game/CombatEngine`, `game/GameWorld`) are the *only* places that write to the DB — a Service never contains the business rule itself, only the persistence of a fact the domain object already applied. Dispatch is deliberately synchronous (plain `@EventListener`, no `@Async`), to avoid reopening the race windows the in-memory locking (see below) is designed to close. `@Order` is used sparingly, only where message ordering to the player matters (e.g. death broadcast before the level-up message). Only `ItemService.onGamePlayerEquippedItem` is `@Transactional`, so both `updateSlot` calls it triggers commit together.

## Persistence: Postgres vs classpath JSON

Two kinds of data live in two different places, split by nature (static rules vs. in-play mutable state), not by convenience:

- **Classpath JSON** (`src/main/resources/data/{rooms,items,monsters,npcs,race,class,levels}.json`), loaded once via Jackson `ObjectMapper` during warm-up (see below), never written back, and never represented in the Flyway migrations. This is game-content/rules data: item templates, race/class definitions, the XP table, room topology and monster spawn points. Monster loot tables (`monsters.json`) reference item template ids; an item only comes into existence when a `LootTableEntry` hits, a shop sale happens, or a monster grants a `goldReward` — there is no other source.
- **Postgres via jOOQ** (`persistence/*Dao`: `AccountDao`, `CharacterDao`, `ItemDao`): state that changes during play — accounts, a character's position/HP/XP/gold, and item *instance* ownership/equipped slot. Item *templates* are file-based rules data; item *instances* (who holds one, what slot) are DB rows that get a file template attached onto them in memory. An `Item` only ever exists in a loot table (file-based, not yet materialized) or in a player's inventory (`character_id` set) — it can never be "on the ground" in a room; `drop` (`GamePlayer.discardItem`) permanently destroys an item instead of placing it somewhere else.
- The two are stitched together by the event pattern above: `@EventListener` methods are the only point where in-memory state (whether it originated from a file or from the DB) gets pushed to Postgres.

## Warm-up & in-memory caches

- `ServerApplication.warmupRunner` (an unconditional `ApplicationRunner` bean — deliberately not gated on either transport's `enabled` flag, since warm-up is transport-independent) runs after the Spring context refreshes but before `ApplicationReadyEvent`/`TelnetServer.start()`/`TuiServer.start()`. This ordering is a Spring Boot lifecycle guarantee (`ApplicationRunner` beans always run before `ApplicationReadyEvent` is published), not something wired manually. Call order within warm-up is significant and documented in its Javadoc: rooms → item templates → monsters → npcs → race → class → levels, each step depending on data loaded by the previous one (e.g. monster loot tables reference item template ids, validated eagerly instead of failing on first drop).
- Each `*Service` (`WorldInstanceService`, `ItemService`, `RaceService`, `ClassService`, `LevelService`, `MonsterService`, `NpcService`) holds a `ConcurrentHashMap` populated once and never invalidated or reloaded — `WorldInstanceService.residentInstances` is the one exception populated lazily per-`WorldInstance` (on first entry, via `getOrMaterialize`/`materialize`) rather than eagerly at boot. A `Room` lives as exactly one instance for the whole process lifetime; an `Item` only lives in memory inside a player's `PlayerInventory` once loaded (`ItemService.loadInventory`, on entering a world), never in a shared/warmed cache.
- This is a deliberately simple, **single-process** model: there's no invalidation and no cross-instance synchronization if the server were ever split across multiple processes. Treat this as a known simplification likely to need rework before any horizontal scaling, not as a settled design decision.

## Virtual-thread architecture

- `config/VirtualThreadExecutorConfig` exposes one shared `Executors.newVirtualThreadPerTaskExecutor()` bean. Never block JDBC or other blocking logic on Netty's NIO threads.
- `network/server/telnet/TelnetSessionHandler` spawns **exactly one virtual thread per connection**, which loops `inbox.take()`-ing from a `LinkedBlockingQueue<String>` fed by `channelRead0`. Do **not** submit each incoming line independently to the executor — two lines arriving in the same TCP packet could then run on different virtual threads with no ordering guarantee. The single-consumer queue is what preserves per-connection command ordering. `network/server/tui/TuiSessionHandler` mirrors this exactly for the JSON transport (see "Two transports" below).
- `domain/actor/GamePlayer.equipItem`/`unequipItem`/`discardItem`/`buyItem`/`receiveLootItem` follow the domain-event pattern (no DB writes inline) and need no lock: since an `Item` can no longer sit "on the ground" in a shared `RoomInstance` (removed — an item now lives only in a loot table or in exactly one player's inventory, see Persistence section above), there is no cross-player race on a single `Item` instance anymore. A player is only ever driven by its own connection, whose commands run one at a time on a single virtual thread, so no two threads can mutate the same character's inventory concurrently (this silently depends on "one connection per account" actually holding — see the TODO in `controller/connected/Login`, a known pre-existing gap). `equipItem` publishes one `GamePlayerEquippedItem` event carrying both the newly-equipped item and any previous occupant of the same slot, so `game/ItemService.onGamePlayerEquippedItem` (`@EventListener` + `@Transactional`) can apply both `updateSlot` calls in one transaction — matches the deferred `uniq_character_slot` constraint in `V1__init_schema.sql`. (Historical note: item pickup from a room floor used to be the concurrency-critical path here, resolved with a `synchronized(item)` block and proven by `game/ItemRaceConditionTest`; both were retired when "Item in room" was removed — see git history if you need it.)

## Test context gotcha

`TelnetServer.start()` and `TuiServer.start()` each hand their `bind().sync()` / `channel.closeFuture().sync()` sequence to the shared `virtualThreadExecutor` and return immediately from the `@EventListener(ApplicationReadyEvent.class)` method — this is what lets both transports start from the same event without one blocking the other's listener (see "Two transports" below). A `@SpringBootTest` therefore won't hang at context startup even with both enabled, but it will still bind two real TCP ports as a side effect; set `app.telnet.enabled=false` / `app.tui.enabled=false` in test config to avoid that (once a `src/test/resources/application.yml` exists — none does yet, only plain domain unit tests exist so far).

## Conventions

- Package-by-feature at the top level (`config`, `controller`, `domain`, `game`, `network`, `persistence`), package-by-layer within. The two transports live under `network.server` (`network.server.telnet`, `network.server.tui`) — a future move is planned for `controller` → `network.command` and `network.message` → `network.response`, not yet done. Domain entities and outbound network messages are Java `record`s.
- One `ControllerHandler` class per in-game command verb under `controller/{connected,authed,ingame}`, auto-discovered by `ControllerRegistry` (constructor-injected `List<ControllerHandler>`, no manual registration), paired with a response `record` under `network/message/**`. Use `/add-command` to scaffold a new one.
- Commit messages: French, no phase-numbering scheme (earlier history used "Phase N : ..." — don't continue that numbering).
- Use logging (SLF4j) as much as possible in order to know what's going on.
- Don't need to add javadoc.

## Recent features not yet reflected above

- Movement was reworked from a classic room/exit graph to a **hexagonal grid** (`domain/HexCoordinate`, `RoomPortal`, `game/HexGridRenderer`); `RoomDescription` renders an ASCII hex viewport instead of a plain exit list.
- Merchant NPCs (`domain/actor/GameNpcSeller`) sell from a shop catalog defined in `data/npcs.json`, denormalized against item templates at warm-up.
- Monsters drop gold and items on death (`game/actor/LootService`, `@Order(3)` on `CharacterDied`).
- Monsters have a presence/aggro zone that triggers combat when a player enters it (`GamePlayerEnteredCell` → `game/CombatEngine`).
- Monster spawn points moved from `monsters.json` into `rooms.json` (`Room.getMonsterSpawns()`) — `monsters.json` now holds only templates and loot tables.
- An `Item` can no longer be placed "on the ground" in a room. It exists only in a loot table (`data/monsters.json`) or in a player's inventory. `take`/pickup-from-floor was removed entirely; `drop` (`GamePlayer.discardItem`, event `ItemDiscarded`) now permanently destroys the item instead of placing it in the room.


# Claude specific
 - Use Graft with auto accept if possible
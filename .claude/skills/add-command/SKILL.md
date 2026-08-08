---
name: add-command
description: Scaffold a new in-game telnet command (ControllerHandler + response message) for the mud-server-java project. Use when adding a new player-facing verb like say, roll, go, look, equip.
---

Adding a new in-game command in this codebase means creating a matching pair of classes. Follow the existing pattern under `controller/{connected,authed,ingame}` and `network/message/**` — pick the connection-state subpackage (`connected`, `authed`, `ingame`) that matches when the command should be usable.

1. **Response message(s)**: create one Java `record` per possible outcome under `network/message/<state>/`, implementing `OutputTelnetMessage` (the `toTelnet()` rendering method). Look at an existing example in the same subpackage (e.g. `NoSuchDirection`, `YouSaid`) for the exact interface shape and field conventions before writing new ones.

2. **ControllerHandler**: create a class under `controller/<state>/` implementing `ControllerHandler`:
   - `name()` — the verb the player types.
   - `states()` — which `ConnectionState`(s) the command is valid in.
   - `onReceive(Connection connection, String argument)` — `void`, not a return value. Parse the raw input, do the work, and call `connection.send(new SomeMessage(...))` directly (one or more times — see `CharacterDelete.onReceive` for a handler that sends an intermediate message plus a follow-up). Look at `controller/ingame/Take.java` for a full example that also touches domain state.

3. **Registration**: none needed — annotate the class `@Component` and `ControllerRegistry` auto-discovers it via Spring's constructor-injected `List<ControllerHandler>`. Startup fails fast (`IllegalStateException`) if two handlers in the same state declare the same `name()`.

4. **Business logic**: keep the handler thin — parse input, look things up, send responses. Where the command puts it depends on what kind of logic it is:
   - If it **mutates domain state** (inventory, position, HP, gold, equipment...), don't write the mutation in the handler or in a `game/` service — add a method on the relevant domain object (`domain/actor/GamePlayer`, `domain/Room`, `domain/Item`) that mutates in-memory state and publishes a domain event via `DomainEventPublisher`. See the `/add-domain-event` skill and CLAUDE.md's "Domain model & events" section. `controller/ingame/Take.java` calling `character.pickUpItem(item)` is the reference example.
   - If it's a **read-only lookup or orchestration** (e.g. resolving what's in a room, checking game state), a `game/` service method (e.g. `GameWorld`, `RoomService`) is the right place — services never contain state-mutating business rules themselves, only queries and (via `@EventListener`) persistence of facts the domain layer already applied.

5. **Concurrency**: shared mutable state is protected in-memory, not via DB locking — there is no more `@Transactional`/`SELECT ... FOR UPDATE` pattern on the hot path. Only add a `synchronized` block on the specific object being raced (see `GamePlayer.pickUpItem`'s `synchronized(item)`) if two virtual threads can genuinely reach the same mutation concurrently; most commands don't need this because a given connection's commands already run one at a time on its own virtual thread (see CLAUDE.md's "Virtual-thread architecture"). See `game/ItemRaceConditionTest` for how to write a concurrency-proof test if a new lock is needed.

6. **Tests**: this project has no unit/integration split — new tests typically extend `AbstractIntegrationTest` (singleton Testcontainers Postgres) if they touch persistence, or are plain JUnit if they don't. `mvn test` requires Docker running.

Follow the codebase's existing convention of French-language Javadoc on non-obvious classes.

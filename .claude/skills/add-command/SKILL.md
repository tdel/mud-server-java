---
name: add-command
description: Scaffold a new in-game telnet command (ControllerHandler + response message) for the mud-server-java project. Use when adding a new player-facing verb like say, roll, go, look, equip.
---

Adding a new in-game command in this codebase means creating a matching pair of classes. Follow the existing pattern under `controller/{connected,authed,ingame}` and `network/message/**` — pick the connection-state subpackage (`connected`, `authed`, `ingame`) that matches when the command should be usable.

1. **Response message(s)**: create one Java `record` per possible outcome under `network/message/<state>/`, implementing `OutputTelnetMessage` (the `toTelnet()` rendering method). Look at an existing example in the same subpackage (e.g. `NoSuchExit`, `YouSaid`) for the exact interface shape and field conventions before writing new ones.

2. **ControllerHandler**: create a class under `controller/<state>/` implementing `ControllerHandler`:
   - `name()` — the verb the player types.
   - `states()` — which `ConnectionState`(s) the command is valid in.
   - `onReceive(Connection session, String argument)` — parse the raw input, do the work (call into `game/` services, not persistence directly), and return the appropriate response record from step 1.

3. **Registration**: none needed — annotate the class `@Component` and `ControllerRegistry` auto-discovers it via Spring's constructor-injected `List<ControllerHandler>`. Startup fails fast (`IllegalStateException`) if two handlers in the same state declare the same `name()`.

4. **Business logic**: if the command needs new game logic beyond simple lookups, put it in the relevant `game/` service (e.g. `GameWorld`, `ItemService`), not in the `ControllerHandler` itself — handlers stay thin and dispatch-focused.

5. **Concurrency**: if the command touches shared mutable state (item ownership, room occupancy), check whether it needs the same pessimistic-locking pattern as `ItemService.addItemToInventory` (`SELECT ... FOR UPDATE` inside `@Transactional`) to stay safe across concurrent virtual threads. See `game/ItemRaceConditionTest` for how to write a concurrency-proof test if so.

6. **Tests**: this project has no unit/integration split — new tests typically extend `AbstractIntegrationTest` (singleton Testcontainers Postgres) if they touch persistence, or are plain JUnit if they don't. `mvn test` requires Docker running.

Follow the codebase's existing convention of French-language Javadoc on non-obvious classes.

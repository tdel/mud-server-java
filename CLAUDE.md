# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

Separately, `docker-compose.yml` at the repo root runs a persistent Postgres for local dev (not for tests): host port **5433** → container 5432 (db `mud-server-java`). The telnet server itself listens on a different port, **4001** (`app.telnet.port` in `application.yml`) — don't confuse the two.

## Stack specifics

- Java 25, Spring Boot 4.1.0. Persistence is jOOQ (`DSLContext`, `spring-boot-starter-jooq`) — a type-safe SQL builder, not an ORM: no persistence-context, no lazy loading, no dirty-checking. Chosen over JPA/Hibernate specifically because DAO call sites never run inside a transaction except `ItemService` (see below), which would fight an ORM's session/entity-lifecycle assumptions.
- The DSL classes (`persistence.jooq.tables.*`, `persistence.jooq.tables.records.*`) are generated at build time (`generate-sources` phase, `jooq-codegen-maven`) directly from the Flyway migration files (`V1__init_schema.sql`, `V2__add_xp.sql`) via `org.jooq.meta.extensions.ddl.DDLDatabase` — **no live database connection is needed for codegen**, it parses the Flyway SQL files themselves. Flyway stays the single source of truth for the schema; jOOQ only reflects it. The plugin's `scripts` property is an Ant-style glob (currently `V*.sql`, matched via `org.jooq.FilePattern` — not a comma-separated list), sorted with `sort=semantic` so `V2` is read after `V1`; a new migration file just needs to match the same `V*.sql` pattern to be picked up, no pom.xml change required.
- DAOs (`persistence/*Dao`) keep a stable public API (`insert`/`findById`/etc.) regardless of the underlying query engine — internals use `dsl.selectFrom(TABLE)...fetch(RowMapperEquivalent)` with a small `private static toDomain(...)` mapper per DAO, replacing the old hand-written `RowMapper` classes.
- Boot 4 split several things out of core that older Boot versions bundled: Flyway needs the dedicated `spring-boot-starter-flyway` (plain `flyway-core` isn't enough) plus `flyway-database-postgresql`; Jackson's `ObjectMapper` bean comes from `spring-boot-starter-json`.
- `spring-security-crypto` is used only for BCrypt — not the full Spring Security starter.
- Tests use AssertJ (`assertThat`), not Mockito — the test suite is integration-style against a real Postgres container rather than mocked.

## Virtual-thread architecture

- `config/VirtualThreadExecutorConfig` exposes one shared `Executors.newVirtualThreadPerTaskExecutor()` bean. Never block JDBC or other blocking logic on Netty's NIO threads.
- `telnet/GameCommandHandler` spawns **exactly one virtual thread per connection**, which loops `inbox.take()`-ing from a `LinkedBlockingQueue<String>` fed by `channelRead0`. Do **not** submit each incoming line independently to the executor — two lines arriving in the same TCP packet could then run on different virtual threads with no ordering guarantee. The single-consumer queue is what preserves per-connection command ordering.
- `domain/Character.pickUpItem` is the concurrency-critical path for item pickup races: item management lives entirely in memory (one live `Item` instance per row for the process lifetime, via `RoomService`/`ItemService`'s warm caches — never reloaded per request), so the race is resolved with a `synchronized(item)` block rather than a DB-level lock — no more `@Transactional`/`SELECT ... FOR UPDATE` on this path (that pessimistic-lock approach was retired; see git history if you need it). `synchronized` no longer pins virtual threads to their carrier since JEP 491 (JDK 24+), so this doesn't violate the "never block a virtual thread" rule above. `game/ItemRaceConditionTest` proves exactly-one-winner semantics using real concurrent virtual threads + a `CyclicBarrier` calling `Character.pickUpItem` directly, and is deliberately **not** `@Transactional` at the test level (the DB write triggered by the `ItemPickedUp` domain event shouldn't share the test thread's connection). `domain/Character.equipItem`/`unequipItem` follow the same domain-event pattern (no DB writes inline) but need no lock at all — a `Character` is only ever driven by its own connection, whose commands run one at a time on a single virtual thread, so no two threads can mutate the same character's inventory concurrently (this silently depends on "one connection per account" actually holding — see the TODO in `controller/connected/Login`, a known pre-existing gap). `equipItem` publishes one `CharacterEquippedItem` event carrying both the newly-equipped item and any previous occupant of the same slot, so `game/ItemService.onCharacterEquippedItem` (`@EventListener` + `@Transactional`) can apply both `updateSlot` calls in one transaction — matches the deferred `uniq_character_slot` constraint in `V1__init_schema.sql`.

## Test context gotcha

`TelnetServer.start()` blocks the calling thread (`channel.closeFuture().sync()`) — it's the app's real main loop, triggered on `ApplicationReadyEvent`. Any `@SpringBootTest` will hang at context startup unless `app.telnet.enabled=false` resolves in that test context (already set in `src/test/resources/application.yml` — keep it that way for new test config).

## Conventions

- Non-obvious classes get French-language Javadoc explaining *why*, not *what* (often comparing against a prior PHP/Swoole implementation being ported). Follow this style for new non-obvious code.
- Package-by-feature at the top level (`config`, `domain`, `game`, `network`, `persistence`, `telnet`), package-by-layer within. Domain entities and outbound network messages are Java `record`s.
- One `ActionHandler` class per in-game command verb under `network/action/{connected,authed,ingame}`, paired with a response `record` under `network/message/**`. Use `/add-command` to scaffold a new one.
- Commit messages: French, no phase-numbering scheme (earlier history used "Phase N : ..." — don't continue that numbering).

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

CLI seed commands run through the same jar via positional args, e.g. `room-create --name=Foo --description=Bar` appended after the `mvn spring-boot:run` invocation, or `java -jar target/*.jar room-create ...` if running the jar directly (see `cli/CliCommandRunner`). Passing any positional arg short-circuits normal boot before the telnet server starts.

## Stack specifics

- Java 25, Spring Boot 4.1.0. Plain JDBC via `NamedParameterJdbcTemplate` — **no JPA/Hibernate**.
- Boot 4 split several things out of core that older Boot versions bundled: Flyway needs the dedicated `spring-boot-starter-flyway` (plain `flyway-core` isn't enough) plus `flyway-database-postgresql`; Jackson's `ObjectMapper` bean comes from `spring-boot-starter-json`.
- `spring-security-crypto` is used only for BCrypt — not the full Spring Security starter.
- Tests use AssertJ (`assertThat`), not Mockito — the test suite is integration-style against a real Postgres container rather than mocked.

## Virtual-thread architecture

- `config/VirtualThreadExecutorConfig` exposes one shared `Executors.newVirtualThreadPerTaskExecutor()` bean. Never block JDBC or other blocking logic on Netty's NIO threads.
- `telnet/GameCommandHandler` spawns **exactly one virtual thread per connection**, which loops `inbox.take()`-ing from a `LinkedBlockingQueue<String>` fed by `channelRead0`. Do **not** submit each incoming line independently to the executor — two lines arriving in the same TCP packet could then run on different virtual threads with no ordering guarantee. The single-consumer queue is what preserves per-connection command ordering.
- `game/ItemService.addItemToInventory` is the concurrency-critical path for item pickup races: `@Transactional` + `ItemDao.findByIdForUpdate` (`SELECT ... FOR UPDATE`) pessimistic lock. `game/ItemRaceConditionTest` proves exactly-one-winner semantics using real concurrent virtual threads + a `CyclicBarrier`, and is deliberately **not** `@Transactional` at the test level (that would pin both virtual threads to the test thread's single connection and defeat the point of the test).

## Test context gotcha

`TelnetServer.start()` blocks the calling thread (`channel.closeFuture().sync()`) — it's the app's real main loop, triggered on `ApplicationReadyEvent`. Any `@SpringBootTest` will hang at context startup unless `app.telnet.enabled=false` resolves in that test context (already set in `src/test/resources/application.yml` — keep it that way for new test config).

## Conventions

- Non-obvious classes get French-language Javadoc explaining *why*, not *what* (often comparing against a prior PHP/Swoole implementation being ported). Follow this style for new non-obvious code.
- Package-by-feature at the top level (`cli`, `config`, `domain`, `game`, `network`, `persistence`, `telnet`), package-by-layer within. Domain entities and outbound network messages are Java `record`s.
- One `ActionHandler` class per in-game command verb under `network/action/{connected,authed,ingame}`, paired with a response `record` under `network/message/**`. Use `/add-command` to scaffold a new one.
- Commit messages: French, no phase-numbering scheme (earlier history used "Phase N : ..." — don't continue that numbering).

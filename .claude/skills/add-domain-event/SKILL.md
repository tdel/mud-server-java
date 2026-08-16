---
name: add-domain-event
description: Scaffold a new domain event (event record + publishing domain-object method + persisting @EventListener) for the mud-server-java project. Use when a new business mutation on GamePlayer/Room/Item/GameMonster needs to be persisted, broadcast, or trigger another system.
---

This codebase keeps domain objects (`domain/actor/GamePlayer`, `domain/Room`, `domain/Item`, `domain/actor/GameMonster`) as plain POJOs that mutate their own in-memory state and publish an event, rather than a Service reaching in and mutating them directly. A `game/`-layer `@EventListener` reacts to the event to persist the fact (and/or notify players, trigger other systems). See CLAUDE.md's "Domain model & events" section for the rationale before scaffolding.

1. **Event record**: add one file under `domain/actor/event/`, named as a past-tense fact (`ItemPickedUp`, `CharacterGainedXp`, `GamePlayerEquippedItem`), carrying exactly the data listeners need — usually the actor plus whatever changed. Give it a short French Javadoc stating which method publishes it and any invariant already guaranteed by the time it's published (e.g. `ItemPickedUp`: "publié une fois que `pickUpItem` a déjà tranché, sous verrou `synchronized(item)`, que `character` remporte l'item — jamais avant"). Look at an existing event in the same package for the exact shape.

2. **Publish it from the domain object**: add or extend a method on the owning domain object (e.g. `CharacterInstance`) that:
   - applies the state change in memory first,
   - then calls `DomainEventPublisher.publish(new YourEvent(...))` — never the other way around, and never call a DAO from here.
   If the mutation is racy under concurrent virtual threads (two connections could plausibly reach it at once — item ownership is the canonical example, character-owned state usually isn't, see CLAUDE.md's "Virtual-thread architecture"), guard the state change with `synchronized` on the specific contended object, not on `this`/the whole domain object.

3. **Listen for it**: add an `@EventListener` method in the relevant `game/`-layer service (`ItemService`, `RoomService`, `game/actor/CharacterService`, `game/actor/LootService`, `game/CombatEngine`, `game/GameWorld` — pick by what the event is about, not by who published it). The listener's job is to reflect the already-applied fact:
   - persist it via the matching `persistence/*Dao` (`insert` if the row doesn't exist yet — e.g. loot/purchases — `update`/`assignTo*` if it does — e.g. moving an existing item),
   - and/or push a message to the player via `event.character().send(new SomeMessage(...))` if the event should also notify them directly (see `ItemService.onCharacterLootedItem` for both in one listener).
   Dispatch is synchronous by default (plain `@EventListener`, no `@Async`) — keep it that way; async dispatch would reopen races the synchronous model was chosen to avoid.

4. **`@Transactional`**: only add it if the listener needs more than one DB write to commit atomically (e.g. `ItemService.onGamePlayerEquippedItem` needs both `updateSlot` calls in one transaction to satisfy the deferred `uniq_character_slot` constraint in `V1__init_schema.sql`). A single-statement listener doesn't need it.

5. **`@Order`**: only add it if message ordering to the player matters relative to another listener on the *same* event (e.g. a death broadcast must arrive before a level-up message — see `RoomService`/`CharacterListener`'s `@Order(1)`/`@Order(2)` on `CharacterDied`). Most listeners don't need it.

6. **Tests**: plain JUnit is enough for the domain-object method itself (no Spring context needed — that's the point of keeping domain objects Spring-free). If the mutation needed a new `synchronized` lock, write a concurrency test the same way `game/ItemRaceConditionTest` does: real virtual threads plus a `CyclicBarrier`, asserting exactly-one-winner semantics, and deliberately not `@SpringBootTest`/`@Transactional` at the test level so the listener's DB write doesn't share the test thread's connection.

Follow the codebase's existing convention of French-language Javadoc on non-obvious classes.

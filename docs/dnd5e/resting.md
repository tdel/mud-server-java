# Resting

Source: [D&D 5e SRD 5.1](https://5thsrd.org/adventuring/resting/) (CC-BY-4.0, Wizards of the Coast)

## Short rest

- ≥1 hour of downtime doing nothing strenuous (eating, reading, tending wounds).
- Spend Hit Dice (up to your level's total) to heal: roll the die + Constitution modifier per die spent, choosing after each roll whether to spend another.
- Most classes' short-rest-recovery features (e.g. Warlock spell slots) key off this.

## Long rest

- ≥8 hours, mostly sleeping; up to 2 hours of light activity (reading, talking, watching) allowed.
- **Interrupted** by ≥1 hour of strenuous activity (walking, fighting, casting) — the rest doesn't count and must restart.
- Restores **all** lost HP.
- Restores spent Hit Dice equal to half the character's total (rounded down, minimum 1).
- Reduces exhaustion by 1 level, provided the creature also ate and drank (see [conditions.md](conditions.md)).
- Max **one** long rest per 24 hours; the character must start the rest with ≥1 HP.

## Notes for this project

Implemented as `rest short`/`rest long` (`controller.ingame.Rest`, `game.actor.RestService`), as a deliberately simplified house rule rather than the RAW mechanic above — several assumed deviations:

- **No Hit Dice pool.** A short rest restores a flat `hitDie/2 + 1 + CON modifier` (minimum 1) per character — the same "average hit die" formula already used for HP-on-level-up (`CharacterService.onCharacterGainedXp`), rather than letting the player spend a variable number of real Hit Dice one roll at a time. A house-rule cap of `GamePlayer.MAX_SHORT_RESTS_BEFORE_LONG_REST` (2) short rests applies before a long rest becomes mandatory to reset the counter — not an SRD rule.
- **Global scope, not per-character.** A short or long rest affects every `GamePlayer` currently online (`GameWorld.onlineCharacters()`), not just the initiator — a deliberate simplification for a MUD without a party/grouping concept, rather than SRD's "each creature decides individually."
- **Food cost instead of time/exhaustion.** A long rest has no in-game time cost and doesn't reduce exhaustion (no exhaustion tracking exists yet, see [conditions.md](conditions.md)); instead it requires the initiator to select `FOOD`-type items (`domain.FoodItem`, sold by the Aubergiste) from their inventory summing to at least `RestService.LONG_REST_PROVISION_THRESHOLD` (20) nutrition value, all of which are consumed regardless of any excess over the threshold. No max-one-per-24h restriction is enforced.
- Restores all missing HP (RAW-accurate) and resets the short-rest counter for every online player, mirroring the RAW "restores spent Hit Dice" effect without tracking Hit Dice at all.

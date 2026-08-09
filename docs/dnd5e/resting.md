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

No rest/downtime system exists yet — healing is currently via potions and (presumably) other in-combat or explicit means. If a rest command is ever added, short-rest Hit Dice spending is the natural fit for a "text-command" MUD action; long-rest exhaustion recovery only matters once [conditions.md](conditions.md) exhaustion tracking exists.

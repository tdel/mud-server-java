# Movement, Environment & Time

Source: [D&D 5e SRD 5.1](https://5thsrd.org/adventuring/) (CC-BY-4.0, Wizards of the Coast)

## Time scales

| Scale | Used for |
|---|---|
| Round (6 sec) | Combat and other fast-paced action |
| Minute | Dungeon exploration — moving down halls, searching a room, checking for traps |
| Hour | City/wilderness travel |
| Day | Long journeys along established routes |

The GM (or, here, the server logic) picks the granularity that matters for the situation.

## Travel pace (assumes 8 hours/day travel)

| Pace | Per minute | Per hour | Per day | Effect |
|---|---|---|---|---|
| Fast | 400 ft | 4 miles | 30 miles | -5 passive Perception |
| Normal | 300 ft | 3 miles | 24 miles | — |
| Slow | 200 ft | 2 miles | 18 miles | Can move stealthily |

**Forced march**: beyond 8 hours/day, each additional hour requires a Constitution save (DC 10, +1 per extra hour past the first); failure = 1 exhaustion level.

**Mounted**: can gallop at double fast pace for about 1 hour before needing rest, or maintain that pace long-distance if fresh mounts are swapped every 8–10 miles.

## Difficult terrain & special movement

- Difficult terrain: 1 ft of movement costs 2 ft (half speed effectively).
- Climbing/swimming: 1 extra ft per ft moved (2 extra in difficult terrain), unless the creature has a climb/swim speed.
- Jumping distance derives from Strength score; Athletics checks may apply for obstacles or difficult landings.

## Falling

`1d6` bludgeoning damage per 10 ft fallen, capped at 20d6 (200 ft). Lands prone (unless damage is avoided some other way).

## Suffocating

- Hold breath: `1 + CON modifier` minutes (minimum 30 seconds if reading literally — SRD treats it as "1 + CON modifier" rounds/minutes depending on context; treat as ≥30s floor).
- After that: survives `CON modifier` more rounds (minimum 1).
- Then: drops to 0 HP and is dying — can't regain HP or stabilize until able to breathe again.

## Vision and light

| Illumination | Obscurement | Effect |
|---|---|---|
| Bright light | None | Normal vision |
| Dim light | Lightly obscured | Disadvantage on sight-based Perception checks |
| Darkness | Heavily obscured | Effectively blinded (see [conditions.md](conditions.md)) |

Special senses: **Darkvision** (see in darkness as dim light, no color), **Blindsight** (perceive without sight in a radius), **Truesight** (sees through illusions/invisibility/normal & magical darkness).

## Food and water

- Food: 1 lb/day (½ lb on subsistence rations). Can go `3 + CON modifier` days (minimum 1) without food before starting to gain 1 exhaustion level per extra day; a full day's food resets the counter.
- Water: 1 gallon/day (2 in hot climates). Drinking only half the requirement: DC 15 CON save or gain 1 exhaustion level. Drinking none: automatic exhaustion (2 levels if already exhausted).

## Notes for this project

The hex-grid movement system (`domain/HexCoordinate`, `RoomPortal`, `game/HexGridRenderer`) currently doesn't model travel pace, terrain cost, or vision/light — every hex move is presumably uniform cost. If terrain-cost or lighting mechanics are ever added to the grid, this file is the reference; exhaustion from forced marches/starvation ties into [conditions.md](conditions.md).

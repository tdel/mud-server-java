# Monsters & NPCs

Source: [D&D 5e SRD 5.1](https://5thsrd.org/gamemaster_rules/) (CC-BY-4.0, Wizards of the Coast)

## Challenge Rating (CR) & XP

CR estimates the threat a monster poses: "a well-equipped, well-rested party of four should defeat a monster of CR equal to their level without a death." CR 0 is trivial; CR 30 is the top of the scale.

| CR | XP |
|---|---|
| 1/8 | 25 |
| 1 | 200 |
| 5 | 1,800 |
| 10 | 5,900 |
| 20 | 25,000 |
| 30 | 155,000 |

XP is normally awarded for defeating a monster, though a GM can award it for neutralizing the threat some other way.

## Size categories

| Size | Space | Hit-die type | Examples |
|---|---|---|---|
| Tiny | 2½×2½ ft | d4 (avg 2½/die) | imp, sprite |
| Small | 5×5 ft | d6 (avg 3½/die) | goblin, giant rat |
| Medium | 5×5 ft | d8 (avg 4½/die) | orc, werewolf |
| Large | 10×10 ft | d10 (avg 5½/die) | ogre, hippogriff |
| Huge | 15×15 ft | d12 (avg 6½/die) | fire giant, treant |
| Gargantuan | 20×20 ft+ | d20 (avg 10½/die) | kraken, purple worm |

HP = hit dice (by size) × count, + CON modifier per die.

## Stat block components

- **AC**: armor/shields/natural armor + DEX modifier.
- **Speed**: walk, plus optional burrow/climb/fly(hover)/swim.
- **Abilities**: all six, as for PCs.
- **Saves/skills**: proficiency bonus scales with CR (not character level).
- **Senses**: passive Perception + blindsight/darkvision/tremorsense/truesight (see [movement-environment.md](movement-environment.md)).
- **Languages**: may include telepathy (mental communication, no shared language needed).
- **Actions**: melee/ranged attacks (bonus + effect); **Multiattack** (several attacks per turn, doesn't apply to opportunity attacks); reactions.
- **Limited-use notations**: `X/Day` (long-rest reset), `Recharge X–Y` (roll d6 at start of turn, regains on X–Y), `Recharge after Rest`.

## Legendary creatures

- **Legendary actions**: usable at the end of *another* creature's turn (not the legendary creature's own), one option at a time; pool refreshes at the start of the legendary creature's turn; unusable while incapacitated or surprised.
- **Lair actions**: on initiative count 20 (losing ties), the creature can trigger one lair-action option while in its lair.
- **Legendary resistance**: (standard DMG rule, not fully quoted in the SRD excerpt fetched) — a legendary creature can choose to succeed on a failed save instead, typically 3 uses per day.
- **Regional effects**: environmental changes tied to the creature's presence; fade once it dies.

## NPCs

NPCs reuse the monster stat-block format. A GM can bolt on racial traits (e.g. a halfling NPC gets 25 ft speed + Lucky) or swap same-level spells without changing CR; changing equipment/weapons or adding strong magic items *can* shift CR, since it changes effective AC/damage output.

## Notes for this project

`game/actor/CharacterService`, `MonsterService`, `NpcService`, and `data/monsters.json`/`npcs.json` back this today, but the game doesn't currently model CR, legendary actions, or size-based hit dice — monster stats are presumably authored directly rather than derived from CR. If CR-based balancing is ever wanted, the XP table above is the anchor point, and it already lines up with [leveling-xp.md](leveling-xp.md)'s character XP thresholds for encounter-difficulty math.

`MonsterTemplate`/`data/monsters.json` now carry a `speed` field (`GameMonster.getSpeed()` overrides `GameCharacter.getSpeed()` to read it), on the same 5-ft-square scale as playable-race speeds in `data/race.json` (SRD ft ÷ 5 — e.g. 30 ft → 6). NPCs (`GameNpc`) have no `speed` field of their own and hardcode `speed = 0` in their constructor, since they don't move; see [movement-environment.md](movement-environment.md) for how `speed` feeds `GameCharacter.getMillisPerCell()`/`MovementTicker`.

# Combat

Source: [D&D 5e SRD 5.1](https://5thsrd.org/combat/) (CC-BY-4.0, Wizards of the Coast)

## Structure

A **round** is ~6 seconds; every participant takes one **turn** per round. Sequence:

1. Determine surprise
2. Establish positions
3. Roll initiative
4. Take turns in initiative order
5. Repeat from step 4 for the next round

**Surprise**: compare each side's Dexterity (Stealth) check to the other's passive Wisdom (Perception). A surprised creature can't move, act, or react on its first turn.

**Initiative**: everyone rolls a Dexterity check (`1d20 + DEX modifier`); highest goes first, order fixed for the whole encounter. Ties broken by GM/player choice.

## A turn

Each turn: move up to your speed (splittable before/between/after actions — see [movement-environment.md](movement-environment.md)) and take **one action**. **Bonus actions** and **reactions** are only available when a class feature, spell, or specific rule grants one. One free object interaction and free communication are also allowed per turn.

### Standard actions

| Action | Effect |
|---|---|
| Attack | One melee or ranged attack (more with Extra Attack) |
| Cast a Spell | Per the spell's casting time, usually 1 action |
| Dash | Gain extra movement equal to your speed |
| Disengage | Your movement doesn't provoke opportunity attacks this turn |
| Dodge | Attacks against you have disadvantage (if you can see the attacker); you get advantage on DEX saves |
| Help | Grant an ally advantage on their next check or attack against a target you're helping against |
| Hide | Dexterity (Stealth) check to become hidden |
| Ready | Prepare an action + trigger, to be used as a reaction before your next turn |
| Search | Perception or Investigation check to find something |
| Use an Object | Interact with an object that needs your full action |

**Opportunity attacks**: when a hostile creature you can see leaves your reach without taking the Disengage action, you can use your reaction for one melee attack against it.

## Making an attack

- Attack roll: `1d20 + ability modifier (STR for melee, DEX for ranged; finesse/thrown are exceptions) + proficiency bonus (if proficient)`. Hit if the total ≥ target AC.
- **Natural 20** always hits; **natural 1** always misses, regardless of modifiers.
- Attacking a target you can't see: disadvantage. Being unseen by your target: advantage against it.
- Making a ranged attack while within 5 ft of a hostile, non-incapacitated creature that can see you: disadvantage.

## Cover

| Cover | AC / DEX save bonus |
|---|---|
| Half (≥50% blocked) | +2 |
| Three-quarters (~75% blocked) | +5 |
| Total (fully blocked) | can't be targeted directly |

Cover bonuses don't stack — only the best applicable degree counts.

## Movement & position (combat-relevant highlights)

- Difficult terrain: every foot costs 1 extra foot of movement.
- Dropping prone: free. Standing up: costs movement equal to half your speed. Crawling while prone: double cost (triple in difficult terrain).
- Moving through a hostile creature's space requires it to be ≥2 sizes larger or smaller than you; you can never end your turn inside another creature's space.
- Squeezing into a space one size too small: movement costs double, attack rolls have disadvantage.

Full movement/terrain/environment rules: [movement-environment.md](movement-environment.md). Damage, healing, and death: [damage-healing-death.md](damage-healing-death.md). Conditions referenced by combat abilities: [conditions.md](conditions.md).

## Notes for this project

This file now documents the SRD reference only, kept for historical comparison — combat itself no longer follows it. The previous turn/initiative-based combat engine (`domain/combat/ActionEconomy`, `domain/combat/CombatEncounter`, `game/CombatEngine`, `game/CombatResult`) was removed in favor of a real-time, cooldown-driven model (no rounds, no action economy, no turn order), and that real-time engine's own attack-roll-vs-AC resolution was in turn replaced by a Lineage2-style model (p.atk/p.def/m.atk/m.def, accuracy/evasion, criticalRate) — see [../lineage2/combat.md](../lineage2/combat.md) for the current implementation. `attack`/`use` (`network/command/ingame/Attack.java`/`Use.java`) are fully implemented, not stubs.

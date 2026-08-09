# Alignment, Backgrounds & Languages

Source: [D&D 5e SRD 5.1](https://5thsrd.org/character/) (CC-BY-4.0, Wizards of the Coast)

## Alignment

Two axes combine into nine alignments. This is flavor/roleplay guidance, not a mechanic with numeric effects (a few spells/monster traits reference alignment directly, e.g. *protection from evil and good*).

| | Lawful | Neutral | Chaotic |
|---|---|---|---|
| **Good** | LG — does right as society expects (paladins, gold dragons) | NG — helps others as needed (most gnomes) | CG — follows conscience over expectation (unicorns) |
| **Neutral** | LN — follows law/tradition/personal code (monks) | N — avoids moral questions (druids, lizardfolk) | CN — personal freedom above all (barbarians, rogues) |
| **Evil** | LE — takes what it wants within a code (devils) | NE — no compassion, no restraint (goblins, drow) | CE — arbitrary violence (demons, red dragons) |

## Backgrounds

A background represents a character's life before adventuring and grants, mechanically:

- **2 skill proficiencies** (fixed per background)
- Usually **1+ tool proficiencies** and/or a **language or two**
- A **starting equipment package**
- A **feature**: a narrative-mechanical perk (e.g. Acolyte's *Shelter of the Faithful* — free healing/support from temples of the character's faith)

Example — **Acolyte**: proficiency in Insight + Religion, two languages, a holy symbol + vestments + 15 gp, and the Shelter of the Faithful feature.

## Languages

Characters know languages from race + background, plus GM-approved extras.

- **Standard**: Common, Dwarvish, Elvish, Giant, Gnomish, Goblin, Halfling, Orc.
- **Exotic** (tied to supernatural origins): Abyssal, Celestial, Draconic, Deep Speech, Infernal, Primordial, Sylvan, Undercommon.
- Some "languages" are actually families of dialects (e.g. Primordial covers Auran/Aquan/Ignan/Terran).
- Most languages share a script with a related language (e.g. Giant/Gnomish/Goblin/Orc all use Dwarvish script); Deep Speech has no script at all.

## Notes for this project

None of this is implemented yet — race (`data/race.json`) currently only carries mechanical traits. If backgrounds or alignment are ever added as character-creation steps, this file is the starting reference; language proficiency would be a natural fit for future multiplayer roleplay/chat gating.

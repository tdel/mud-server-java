# Spellcasting

Source: [D&D 5e SRD 5.1](https://5thsrd.org/spellcasting/) (CC-BY-4.0, Wizards of the Coast)

## What is a spell

A discrete magical effect with a **level** from 0–9 (0 = **cantrip**), indicating power. Casting 9th-level spells requires (typically) 17th character level.

- **Known casters** (Bard, Sorcerer, Warlock, ...): a fixed spell list always available.
- **Prepared casters** (Cleric, Druid, Wizard, Paladin, ...): choose a subset from their full class list each day, count scales with level.
- **Spell slots**: casting a spell of level N expends a slot of level N or higher; slots recover on a long rest (Warlock's Pact Magic slots recover on a short rest instead — see [classes.md](classes.md)).
- **Cantrips**: cast at will, no slot, no preparation needed.
- **Rituals**: a ritual-tagged spell can be cast without a slot by adding 10 minutes to its casting time, if the caster has the ritual casting feature and knows/has prepared it.

## Casting a spell

- **Casting time**: usually 1 action; some are bonus action, reaction (triggered), or longer (requires concentration-like sustained casting each turn). Casting a bonus-action spell blocks casting another spell that turn except a 1-action cantrip.
- **Range**: feet, or `touch`/`self`. Area effects centered on you use `self`.
- **Components**:
  - **Verbal (V)**: spoken; blocked by silence or being gagged.
  - **Somatic (S)**: gesture; needs a free hand.
  - **Material (M)**: a specified object, substitutable by a component pouch/focus unless the spell consumes or costs gp for the component.
- **Duration**: instantaneous (can't be dispelled), timed, or **concentration**. Concentration breaks on: casting another concentration spell, taking damage (CON save DC = `10 or half the damage taken, whichever is higher`), or being incapacitated/killed.
- **Targeting**: needs a clear path (total cover blocks); can target self unless the spell says otherwise.

## Spell save DC & attack bonus

- **Save DC** = `8 + proficiency bonus + spellcasting ability modifier`
- **Spell attack bonus** = `proficiency bonus + spellcasting ability modifier`

## Stacking

Different spells' effects stack; the same spell cast multiple times on one target does **not** stack — only the strongest instance applies.

## Notes for this project

No spellcasting system exists yet — `data/class.json`/`ClassService` don't carry spell lists or slot tables. If spellcasting is ever added, the save-DC/attack-bonus formulas above reuse the existing ability-modifier and proficiency-bonus math from [ability-scores.md](ability-scores.md), and slot/level gating would hook into [leveling-xp.md](leveling-xp.md).

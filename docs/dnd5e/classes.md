# Classes

Source: [D&D 5e SRD 5.1](https://5thsrd.org/character/classes/) (CC-BY-4.0, Wizards of the Coast)

There are 12 core classes. Each defines a hit die (HP gained per level), a primary ability, two saving-throw proficiencies, armor/weapon proficiencies, and (for 9 of the 12) a spellcasting ability. A subclass ("archetype") is chosen at a class-specific level (usually 1st, 2nd, or 3rd) and layers extra features on top.

| Class | Hit die | Primary ability | Saves | Spellcasting | Core mechanic |
|---|---|---|---|---|---|
| Barbarian | d12 | Strength | STR, CON | — | Rage: damage resistance, adv. on STR checks, bonus melee damage |
| Bard | d8 | Charisma | DEX, CHA | Charisma | Bardic Inspiration: grants allies a bonus die |
| Cleric | d8 | Wisdom | WIS, CHA | Wisdom | Channels a divine Domain to fuel spells and support |
| Druid | d8 | Wisdom | INT, WIS | Wisdom | Wild Shape: transforms into a beast (2nd level) |
| Fighter | d10 | Strength | STR, CON | — | Extra Attack + Action Surge for raw combat throughput |
| Monk | d8 | Dexterity | STR, DEX | — | Ki points fuel bonus unarmed strikes, defense, stunning strikes |
| Paladin | d10 | Charisma | WIS, CHA | Charisma | Divine Smite (radiant melee burst) + Lay on Hands healing |
| Ranger | d10 | Wisdom (Dex secondary) | STR, DEX | Wisdom | Favored Enemy / Natural Explorer: tracking & terrain bonuses |
| Rogue | d8 | Dexterity | DEX, INT | — | Sneak Attack: bonus 1d6+ damage with advantage or an ally adjacent |
| Sorcerer | d6 | Charisma | CON, CHA | Charisma | Innate magic shaped via Sorcerous Origin + Metamagic |
| Warlock | d8 | Charisma | WIS, CHA | Charisma | Pact Magic: few slots, but they recharge on a short rest |
| Wizard | d6 | Intelligence | INT, WIS | Intelligence | Prepares spells from a spellbook; recovers slots via short-rest study |

## Armor/weapon proficiency highlights

- **Fighter**: all armor, shields, simple + martial weapons — the broadest proficiency list.
- **Barbarian**: light/medium armor, shields, simple + martial weapons.
- **Monk**: no armor; simple weapons + shortswords only (relies on unarmored defense).
- **Rogue**: light armor; simple weapons, hand crossbows, longswords, rapiers, shortswords.
- Spellcasters other than clerics/druids/paladins tend to have the lightest proficiency lists, trading martial versatility for spell access.

## Notes for this project

Proficiency bonus scaling ([ability-scores.md](ability-scores.md)) and the XP/level table ([leveling-xp.md](leveling-xp.md)) are shared across all classes — hit dice are the one per-class number that feeds directly into HP-on-level-up if/when this game implements per-class leveling. Currently `data/class.json` + `data/levels.json` back `ClassService`/`LevelService`; align any new class data against the table above.

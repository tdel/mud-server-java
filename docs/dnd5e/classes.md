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

`primaryAbility`, weapon proficiency, and armor proficiency (including shields) are now implemented end-to-end: `data/class.json` carries all three per class, `ClassService` exposes them, and `GameWorld.createCharacter`/`CharacterDao` bake them onto `GamePlayer` at creation. `primaryAbility` is pure data (shown on the stats screen and during class selection, `network/message/ingame/GamePlayerStats`/`network/message/authed/ChooseClass`) — it has no effect on ability-score generation. Weapon and armor proficiency are actually enforced in `game/CombatService`/`game/CheckService`:

- **Weapon**: a non-proficient weapon loses its attack-roll proficiency bonus (`GamePlayer.getWeaponProficiencies()`), mirroring how `CheckService` already gates skill/save proficiency.
- **Armor**: wearing any equipped piece requiring an `ArmorProficiency` the class doesn't have (`GamePlayer.isWearingNonProficientArmor()`) imposes SRD disadvantage on STR/DEX checks, saves, and attack rolls — implemented as a genuine "roll 2d20, keep the lower" mechanic (`DiceRoller.rollD20`), not a hard equip block. Equipping is never refused either way; non-proficiency only ever penalizes a roll, consistent with this project's general preference for penalizing over gatekeeping equip choices.
- The SRD's "can't cast spells while wearing non-proficient armor" clause is not applicable — no spellcasting system exists yet ([spellcasting.md](spellcasting.md)).
- `CombatService.rollInitiative` and `tryMonsterAttack` are deliberately excluded from the disadvantage mechanic: initiative was never in scope, and monsters have no class/proficiencies to check.

Two simplifications worth knowing about if this area needs revisiting:

- **Weapon proficiency is a coarse SIMPLE/MARTIAL approximation**, not the SRD's real per-class named-weapon lists. Barbarian/Fighter/Paladin/Ranger keep full martial proficiency; the other 8 classes (Bard, Cleric, Druid, Monk, Rogue, Sorcerer, Warlock, Wizard) are modeled as simple-only, even though several of them have a few named martial exceptions in the real SRD (e.g. Rogue: hand crossbows, longswords, rapiers, shortswords; Monk: shortswords). Concretely, a Rogue or Monk equipping the shipped "Epée courte" (shortsword, tagged `MARTIAL`) loses their attack proficiency bonus — not SRD-accurate, but the item model has no finer-grained weapon categorization to represent the real exception lists. A SIMPLE weapon ("Dague") was added to `data/items.json` so non-martial classes have something to equip with their own proficiency intact.
- **Shields are modeled via `ItemType.SHIELD`, not `ArmorCategory`** — a shield never has an `armorCategory` in `data/items.json` (light/medium/heavy only describes body armor), so `ArmorProficiency.SHIELDS` is derived from the item's type, never from `ArmorProficiency.of(ArmorCategory)`.

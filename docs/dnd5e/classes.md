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

**Deviation from SRD**: the 12 classes above are no longer implemented. `CharacterClass` (`domain/actor/CharacterClass.java`, `data/class.json`) now has exactly two starting classes:

| Class | Hit die | Primary ability | Saves | Weapon prof. | Armor prof. | Mana/level | Starting weapon |
|---|---|---|---|---|---|---|---|
| `FIGHTER` | d10 | Strength | STR, CON | Simple + Martial | Light/Medium/Heavy/Shields | 5 | Epée courte (`items.json`) |
| `MYSTIC` | d8 | Intelligence | INT, WIS | Simple | none | 15 | Bâton de magicien basique (`items.json`) |

Both classes get the same "normal attack" (melee, `CharacterCombat.attack`, unaffected by class) and full access to the spell catalog (`data/spells.json`, every spell's `classes` field is `["MYSTIC"]` — Fighter never casts spells). Starting weapon is granted and auto-equipped at creation by `game/engine/StartingEquipmentEngine` (`@EventListener` on `NewGamePlayerCreated`).

**Subclasses** (new concept, absent from the SRD): at level 20, a character picks a subclass among a fixed list depending on their base class (`Subclass.availableAt(CharacterClass, tier=1)`): `FIGHTER` → Warrior/Knight/Rogue, `MYSTIC` → Wizard/Cleric. A second pick happens at level 40 (`tier=2`), currently returning an empty list — the mechanism (`CharacterInstance.getPendingSubclassTier`/`chooseSubclass`, command `choose-subclass`) already supports it, content is just not designed yet. **Subclasses are a pure label today — no mechanical effect** (no extra proficiencies/stats); `getPrimaryAbility()`/`getWeaponProficiencies()`/etc. still delegate only to `CharacterClass`.

The rest of the DnD5e-derived mechanics (proficiency bonus scaling, weapon/armor proficiency enforcement via `CharacterInstance.check`/`save`/`CharacterCombat.attack`, the SIMPLE/MARTIAL weapon-category approximation, shields modeled via `ItemType.SHIELD`) are unchanged from before this refactor — only the *set* of classes shrank, not how proficiency/attack/AC math works.

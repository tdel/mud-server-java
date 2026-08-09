# Races (Species)

Source: [D&D 5e SRD 5.1](https://5thsrd.org/character/races/) (CC-BY-4.0, Wizards of the Coast)

Every playable race grants: an ability score increase, an age range, a typical alignment tendency, a size category, a base walking speed, and a set of special traits (often including darkvision and bonus languages). Many races also have subraces layering extra bonuses on top.

## Summary table

| Race | Ability increase | Size | Speed | Darkvision | Key traits |
|---|---|---|---|---|---|
| Dragonborn | STR +2, CHA +1 | Medium | 30 ft | No | Draconic ancestry, breath weapon (2d6→5d6 by level 16), damage resistance matching ancestry |
| Dwarf | CON +2 | Medium | 25 ft (unaffected by heavy armor) | 60 ft | Resilience (adv. + resistance to poison), combat training (axes/hammers), tool proficiency, stonecunning |
| Elf | DEX +2 | Medium | 30 ft | 60 ft | Keen Senses (Perception proficiency), Fey Ancestry (adv. vs charm, immune to magical sleep), Trance (4h rest) |
| Gnome | INT +2 | Small | 25 ft | 60 ft | Gnome Cunning (adv. on INT/WIS/CHA saves vs magic) |
| Half-Elf | CHA +2, two others +1 | Medium | 30 ft | 60 ft | Fey Ancestry, two bonus skill proficiencies |
| Half-Orc | STR +2, CON +1 | Medium | 30 ft | 60 ft | Menacing (Intimidation), Relentless Endurance (drop to 1 hp instead of 0, 1/long rest), Savage Attacks (extra crit die) |
| Halfling | DEX +2 | Small | 25 ft | No | Lucky (reroll natural 1s), Brave (adv. vs frightened), Halfling Nimbleness (move through larger creatures' spaces) |
| Human | all abilities +1 | Medium | 30 ft | No | Extra language; no other mechanical specialization |
| Tiefling | INT +1, CHA +2 | Medium | 30 ft | 60 ft | Hellish Resistance (fire), Infernal Legacy (*thaumaturgy*, later *hellish rebuke*, *darkness*) |

## Subraces

- **Hill Dwarf**: WIS +1; Dwarven Toughness (+1 max HP, +1 per level).
- **High Elf**: INT +1; weapon training (longsword, shortsword, longbow, shortbow); one wizard cantrip (INT-based); one extra language.
- **Rock Gnome**: CON +1; Artificer's Lore (2× proficiency bonus on INT (History) checks re: magic items/tech); Tinker (build tiny clockwork devices).
- **Lightfoot Halfling**: CHA +1; Naturally Stealthy (can hide behind a creature one size larger).

## Notes for this project

`data/race.json` mirrors the ability-score increases and speed values above for all 9 races (each subrace listed in "Subraces" gets its own `Race` enum value layering its bonus on top of the base race, e.g. `HILL_DWARF`, `HIGH_ELF`, `ROCK_GNOME`, `LIGHTFOOT_HALFLING` — there's no in-game choice-of-subrace flow) — see `RaceService.speed`/`attributeScoreBonuses`, applied to a character's `GameCharacter.speed` at creation (`GameWorld.createCharacter`) and at load (`CharacterDao.toDomain`). One RAW deviation: Half-Elf's two "ability of your choice" +1s are fixed to INT/WIS rather than player-chosen, since no race in this project supports a bonus-choice mechanism yet. Darkvision and other passive traits are still the kind of thing to encode as flags if/when a vision/lighting system exists. Alignment is flavor text more than a mechanical system — see [backgrounds-alignment.md](backgrounds-alignment.md).

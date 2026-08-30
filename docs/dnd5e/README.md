# DnD5e Rules Reference

Reference documentation for D&D 5th Edition rules, summarized from the [D&D 5e SRD 5.1](https://5thsrd.org/) (CC-BY-4.0, Wizards of the Coast). This project targets DnD5e compliance for the systems below (see `CLAUDE.md`), so these files exist as a scoped, load-on-demand reference: pull in only the file(s) relevant to the system being touched, instead of re-deriving or guessing SRD rules from general knowledge each time. **Combat itself is no longer DnD5e** — see [../lineage2/README.md](../lineage2/README.md) for the current attack/damage/defense model.

Each file ends with a **"Notes for this project"** section connecting the rule to the current Java implementation (or noting that it isn't implemented yet).

| File | Covers |
|---|---|
| [ability-scores.md](ability-scores.md) | The six abilities, modifiers, ability checks, saving throws, advantage/disadvantage, proficiency bonus, inspiration |
| [races.md](races.md) | Playable races/subraces: ability increases, size, speed, traits |
| [classes.md](classes.md) | SRD's 12 classes (reference only) + this project's actual Fighter/Mystic + subclasses system |
| [backgrounds-alignment.md](backgrounds-alignment.md) | Alignment grid, backgrounds, languages |
| [leveling-xp.md](leveling-xp.md) | XP table, proficiency bonus by level, HP on level-up, multiclassing |
| [combat.md](combat.md) | Turn/round structure, initiative, actions, attack rolls, cover |
| [conditions.md](conditions.md) | All 15 conditions + exhaustion levels |
| [damage-healing-death.md](damage-healing-death.md) | Damage types, resistance/vulnerability, 0 HP, death saves, healing |
| [resting.md](resting.md) | Short rest / long rest recovery rules |
| [movement-environment.md](movement-environment.md) | Travel pace, difficult terrain, falling, suffocating, vision/light, food/water |
| [equipment.md](equipment.md) | Currency, weapons (categories/properties), armor, adventuring gear |
| [magic-items.md](magic-items.md) | Rarity tiers, weapon/armor bonuses, attunement, activation |
| [spellcasting.md](spellcasting.md) | Spell levels/slots, casting time/components/duration, save DC |
| [feats.md](feats.md) | Feats as an ASI alternative |
| [monsters-npcs.md](monsters-npcs.md) | CR/XP, size categories, stat blocks, legendary creatures, NPC customization |

See also [../lineage2/README.md](../lineage2/README.md) for the current Lineage2-style combat model (p.atk/p.def/m.atk/m.def, accuracy/evasion, criticalRate), which replaced the `combat.md` attack-roll-vs-AC resolution described below.

## Scope note

This is a working reference, not a verbatim SRD reproduction — each file paraphrases/summarizes the source rather than copying full legal text, and links back to the source page. For anything not covered here or where precision matters (exact spell text, full class feature tables, complete item lists), fetch the primary source directly rather than assuming these files are exhaustive.

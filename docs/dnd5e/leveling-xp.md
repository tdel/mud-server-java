# Leveling Up, Experience & Multiclassing

Source: [D&D 5e SRD 5.1](https://5thsrd.org/rules/leveling_up/) (CC-BY-4.0, Wizards of the Coast)

## Character Advancement Table

| Level | XP required | Proficiency bonus |
|---|---|---|
| 1 | 0 | +2 |
| 2 | 300 | +2 |
| 3 | 900 | +2 |
| 4 | 2,700 | +2 |
| 5 | 6,500 | +3 |
| 6 | 14,000 | +3 |
| 7 | 23,000 | +3 |
| 8 | 34,000 | +3 |
| 9 | 48,000 | +4 |
| 10 | 64,000 | +4 |
| 11 | 85,000 | +4 |
| 12 | 100,000 | +4 |
| 13 | 120,000 | +5 |
| 14 | 140,000 | +5 |
| 15 | 165,000 | +5 |
| 16 | 195,000 | +5 |
| 17 | 225,000 | +6 |
| 18 | 265,000 | +6 |
| 19 | 305,000 | +6 |
| 20 | 355,000 | +6 |

## Hit points on level up

Each level grants one additional Hit Die. Either:
- **Roll** the class's Hit Die + Constitution modifier and add to max HP, or
- **Take the fixed average** the class lists instead of rolling (common default for consistency).

If Constitution modifier increases retroactively, HP max increases by 1 per already-attained level for each point gained.

**No longer the model used by this project** — max HP (and max mana) moved to a Lineage2-style CON/MEN-driven formula; see `docs/lineage2/combat.md`'s "Health & Mana pools".

## Ability Score Improvements

At class-specific levels (commonly 4, 8, 12, 16, 19), a character may increase one ability score by 2, or two different scores by 1 each, capped at 20 per score (some class features can exceed this cap).

## Multiclassing

Optional rule: a character can add levels in a second class instead of leveling up their first, subject to ability-score prerequisites in **both** the current and new class (e.g. Barbarian needs STR 13, Wizard needs INT 13, Paladin needs STR 13 *and* CHA 13, Monk needs DEX 13 *and* WIS 13).

- Gaining the first level in a new class grants only a **subset** of that class's normal 1st-level proficiencies (e.g. a new Fighter/Paladin level grants light+medium armor, shields, simple+martial weapons; Sorcerer/Wizard grant no new proficiencies at all).
- **Multiclass spell slots**: add together all levels in Bard/Cleric/Druid/Sorcerer/Wizard, plus *half* (rounded down) of Paladin/Ranger levels, and look up the total on the Multiclass Spellcaster slot table — not simply the sum of each class's own slot table.

## Notes for this project

**Deviation from SRD**: max level was extended from 20 to 40 to make room for a second subclass pick at level 40 (see [classes.md](classes.md)). `data/levels.json` keeps the table above verbatim for levels 1-20, then continues with a homebrew curve for levels 21-40 (no SRD source beyond 20) — `LevelCatalog.maxLevel()` is just the size of that list, no code change needed to extend it further. Proficiency bonus (`CharacterInstance.getProficiencyBonus()`, `2 + (level-1)/4`) is a pure formula, not a table, so it naturally extrapolates past level 20 (+11 at level 40). Multiclassing is still not modeled (single class per character); subclass choice (level 20/40) is unrelated to multiclassing — it's a same-class specialization label with no separate proficiency/spell-slot rules.

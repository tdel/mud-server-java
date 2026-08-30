# Combat

See `docs/lineage2/README.md` for the caveat on these formulas' provenance (community-reconstructed, not an exact retail reproduction). Implementation: `app.game.combat.CombatFormulas` (pure functions, no RNG) plus the RNG orchestration in `CharacterCombat.attack`, `MonsterInstance.attack`, and `SpellCasting.rollDamage`/`castModifier`.

## Derived combat stats

Every `AbstractCharacter` exposes 7 derived stats, computed on the fly from its 6 attributes (STR/DEX/CON/INT/WIT/MEN), level, and equipped items — never persisted, same as the old `armorClass` was not:

| Stat | Formula | Driven by |
|---|---|---|
| P.Atk | `weaponPAtk * statBonus(STR) * levelFactor(level)` | equipped weapon, STR, level |
| M.Atk | `weaponMAtk * statBonus(INT) * levelFactor(level)` | equipped weapon, INT, level |
| P.Def | `sum(equipped pDef) + statBonus(CON) * BASE_DEF_FACTOR` | equipped armor, CON |
| M.Def | `sum(equipped mDef) + statBonus(MEN) * BASE_DEF_FACTOR` | equipped armor, MEN |
| Accuracy | `BASE_ACCURACY + level + statBonus(DEX) * ACCURACY_FACTOR + itemBonus` | DEX, level, items |
| Evasion | `BASE_EVASION + level + statBonus(DEX) * EVASION_FACTOR - armorWeightPenalty + itemBonus` | DEX, level, armor weight, items |
| Critical Rate | `BASE_CRIT_RATE + statBonus(DEX) * ACCURACY_FACTOR + itemBonus`, clamped to [1, 90]% | DEX, items |

`statBonus(score) = 1.03^(score - 10)` — a smooth exponential centered on score 10 (neutral, bonus = 1.0), used consistently across all 6 attributes instead of DnD5e's `(score-10)/2` linear modifier. `levelFactor(level) = 1 + (level-1) * 0.02`.

Each stat has an `effective` variant (`getEffectivePAtk()`, etc.) that adds any active buff/debuff from `ActiveEffects` (`ModifiedStat.PATK/PDEF/MATK/MDEF/ACCURACY/EVASION`) — this is what combat resolution actually reads, exactly like `getEffectiveArmorClass()` did before.

`AbstractCharacter` exposes these via 8 `protected` hooks (`basePAtk()`, `baseMAtk()`, `basePDefSum()`, `baseMDefSum()`, `accuracyItemBonus()`, `evasionItemBonus()`, `armorWeightPenalty()`, `critItemBonus()`) with neutral defaults; `CharacterInstance` sums its equipped items, `MonsterInstance` reads its `MonsterTemplate`'s natural values (monsters keep a full STR/DEX/CON/INT/WIT/MEN block, same as players — their "weapon+armor" is just baked into the template's natural pAtk/pDef/etc.).

## Resolving a hit

Used identically for a melee attack and a damage spell (physical uses P.Atk/P.Def, magic uses M.Atk/M.Def):

1. `hitChance = accuracy / (accuracy + evasion)`, clamped to `[0.20, 0.98]` — accuracy/evasion are opposed, never a guaranteed hit or miss.
2. Roll `DiceRoller.rollChance(hitChance)`. Miss → 0 damage.
3. On a hit, roll critical independently: `DiceRoller.rollChance(criticalRate / 100.0)`.
4. Roll damage variance: `DiceRoller.randomVariance(0.9, 1.1)`.
5. `damage = max(1, round(atk * (atk / (atk + def)) * variance * (critical ? 2.0 : 1.0)))`.

A spell's `effectDice` (from `data/spells.json`) is added to the caster's effective M.Atk before this resolution, so spell tiers of the same name (e.g. a damage spell's tier 1 vs tier 5) still scale meaningfully instead of all hitting identically — a deliberate refinement over a pure "read M.Atk only" approach, since the L2-inspired ratio formula alone would otherwise flatten tier progression.

## What didn't change

Buff/debuff spells (`ModifiedStat.PATK/PDEF/MATK/MDEF/ACCURACY/EVASION`), heals, cast time, per-spell cooldown, and the melee attack cooldown (`CharacterCombat.ATTACK_COOLDOWN`, 2s) are untouched — only the hit/damage resolution formula moved from DnD5e (d20 + modifier vs AC) to this ratio-based model. Peace zones still block all combat the same way (`AbstractZone`/`PeaceZone`).

## Removed: weapon proficiency

The DnD5e-era `WeaponCategory` (SIMPLE/MARTIAL) and the associated "non-proficient weapon" penalty were removed outright — Lineage2 has no equivalent concept, and there was no other use for it in the codebase. Armor proficiency (`ArmorProficiency`, used only by `checkOrSave`'s disadvantage-on-skill-check rule) is untouched and still DnD5e-flavored, since it's unrelated to combat resolution.

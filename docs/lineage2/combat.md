# Combat

See `docs/lineage2/README.md` for the caveat on these formulas' provenance (community-reconstructed, not an exact retail reproduction). Implementation: `app.game.combat.CombatFormulas` (pure functions, no RNG) plus the RNG orchestration in `CombatSystem.attack`, `MonsterInstance.attack`, and `SkillSystem.rollDamage`/`castModifier`.

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
| P.Crit (`getCriticalRate()`) | `BASE_CRIT_RATE + statBonus(DEX) * ACCURACY_FACTOR + itemBonus`, clamped to [1, 90]% | DEX, items |
| M.Crit (`getMagicalCriticalRate()`) | `BASE_CRIT_RATE + statBonus(WIT) * ACCURACY_FACTOR + itemBonus`, clamped to [1, 90]% | WIT, items |

`statBonus(score) = 1.03^(score - 10)` — a smooth exponential centered on score 10 (neutral, bonus = 1.0), used consistently across all 6 attributes instead of DnD5e's `(score-10)/2` linear modifier. `levelFactor(level) = 1 + (level-1) * 0.02`.

Each stat has an `effective` variant (`getEffectivePAtk()`, etc.) that adds any active buff/debuff from `EffectsSystem` (`ModifiedStat.PATK/PDEF/MATK/MDEF/ACCURACY/EVASION/PCRIT/MCRIT`) — this is what combat resolution actually reads, exactly like `getEffectiveArmorClass()` did before. M.Crit (`CombatFormulas.magicCriticalRate`, driven by WIT) is a straight mirror of P.Crit's DEX formula: WIT had no formula reading it at all before this — the only attribute of the six that was otherwise inert.

`AbstractCharacter` exposes these via 8 `protected` hooks (`basePAtk()`, `baseMAtk()`, `basePDefSum()`, `baseMDefSum()`, `accuracyItemBonus()`, `evasionItemBonus()`, `armorWeightPenalty()`, `critItemBonus()`) with neutral defaults; `CharacterInstance` sums its equipped items, `MonsterInstance` reads its `MonsterTemplate`'s natural values (monsters keep a full STR/DEX/CON/INT/WIT/MEN block, same as players — their "weapon+armor" is just baked into the template's natural pAtk/pDef/etc.).

## Health & Mana pools

Like the derived combat stats above, max HP and max mana follow CON/MEN via `statBonus`, but
scale **linearly with level** instead of using `levelFactor()`: HP/mana have no equipment
lever to carry progression the way p.atk/p.def do (no item grants a flat HP/mana bonus in this
project), so level itself has to drive growth directly, with CON/MEN modulating it as a build
choice — same relationship as CON→P.Def and MEN→M.Def, just applied to the vitals pool instead
of a defense stat.

| Stat | Formula | Driven by |
|---|---|---|
| Max HP | `classHitDie * level * statBonus(CON)` | class (`hitDie`), level, CON |
| Max Mana | `classManaGainPerLevel * level * statBonus(MEN)` | class (`manaGainPerLevel`), level, MEN |
| HP regen/tick | `maxHealth * HP_REGEN_RATE * statBonus(CON)` | max HP, CON |
| Mana regen/tick | `maxMana * MP_REGEN_RATE * statBonus(MEN)` | max mana, MEN |

`classHitDie`/`classManaGainPerLevel` are the same `CharacterClass` fields the old DnD5e
model used (hit die, mana-per-level) — reused here purely as a per-class base multiplier, no
JSON change needed. Unlike p.atk/p.def, `maxHealth`/`maxMana` stay **persisted fields**
(`CharacterInstance`, recomputed at character creation, level-up, and on every DB load in
`CharacterDao.toDomain`) rather than recomputed on every read: CON/MEN never change after
character creation in this project (no stat allocation, no attribute-granting gear), so
recomputing only at those three points is equivalent to a fully derived stat, without the
larger refactor a truly always-derived HP/mana would require (see `CombatFormulas.maxHealth`/
`maxMana`/`healthRegenPerTick`/`manaRegenPerTick`).

## Resolving a hit

Used identically for a melee attack and a damage skill (physical uses P.Atk/P.Def, magic uses M.Atk/M.Def):

1. `hitChance = accuracy / (accuracy + evasion)`, clamped to `[0.20, 0.98]` — accuracy/evasion are opposed, never a guaranteed hit or miss.
2. Roll `DiceRoller.rollChance(hitChance)`. Miss → 0 damage.
3. On a hit, roll critical independently: `DiceRoller.rollChance(criticalRate / 100.0)` — melee/`CombatSystem` and monster attacks use effective P.Crit, damage skills (`SkillSystem.rollDamage`) use effective M.Crit.
4. Roll damage variance: `DiceRoller.randomVariance(0.9, 1.1)`.
5. `damage = max(1, round(atk * (atk / (atk + def)) * variance * (critical ? 2.0 : 1.0)))`.

A skill's `power` (a flat, per-tier integer from `data/skills/skills.json`) is added to the caster's effective M.Atk before this resolution, so skill tiers of the same name (e.g. a damage skill's tier 1 vs tier 5) still scale meaningfully instead of all hitting identically — a deliberate refinement over a pure "read M.Atk only" approach, since the L2-inspired ratio formula alone would otherwise flatten tier progression.

## Elemental resistance

A damage skill whose `Skill.element()` (`SkillElement`: FIRE/WATER/WIND/EARTH/HOLY/DARK, or NONE for a physical/neutral skill) is not `NONE` has `CombatFormulas.applyElementalResistance(rawDamage, resistScore)` applied on top of step 5 above, in `SkillSystem.rollDamage`: `multiplier = clamp(1 - resistScore * 0.01, [0.1, 2.0])`, `damage = max(1, round(rawDamage * multiplier))`. `resistScore` reads `target.getElementalResistance(element)` — a positive score (resistance) reduces damage down to ×0.1, a negative one (vulnerability) amplifies it up to ×2. It sums `ItemTemplate.elementalResistances`/`MonsterTemplate.elementalResistances` (see `docs/lineage2/equipment.md`) across a character's equipped items, or reads a monster's natural resistances directly — never persisted, computed on the fly like every other derived stat. Melee/`CombatSystem` damage is untouched: elemental resistance only applies to skill damage, since melee has no element.

## Buffs/debuffs

`ActiveEffect.category()` derives `BUFF`/`DEBUFF` (`EffectCategory`) from `amount`'s sign — no separate persisted field. `EffectsSystem` caps active effects per category: `MAX_BUFF_SLOTS` (6), `MAX_DEBUFF_SLOTS` (4). Applying a new effect (not a refresh of an already-active `skillId`) while a category is full evicts the soonest-to-expire effect of that same category; `SkillSystem.castModifier` publishes `CharacterEffectExpired` for the evicted effect, reusing the exact same expiry pipeline (`ActiveEffectEngine`/`ActiveEffectPersistenceListener`) a natural timeout goes through — no new event type needed.

A debuff can additionally be resisted independently of the skill's normal hit/evasion check: `CombatFormulas.debuffResistChance(menScore)` — `clamp(0, 70, round(5 + statBonus(MEN) * 3.0)) / 100.0` — rolled right after a successful hit in `SkillSystem.castModifier`. A resist is reported the same way as a miss (`CastOutcome.hit() == false`) — there's no separate "resisted" status on the wire yet.

## What didn't change

Buff/debuff skills (`ModifiedStat.PATK/PDEF/MATK/MDEF/ACCURACY/EVASION`), heals, cast time, per-skill cooldown, and the melee attack cooldown (`CombatSystem.ATTACK_COOLDOWN`, 2s) are untouched — only the hit/damage resolution formula moved from DnD5e (d20 + modifier vs AC) to this ratio-based model. Peace zones still block all combat the same way (`AbstractZone`/`PeaceZone`).

## Removed: weapon proficiency

The DnD5e-era `WeaponCategory` (SIMPLE/MARTIAL) and the associated "non-proficient weapon" penalty were removed outright — Lineage2 has no equivalent concept, and there was no other use for it in the codebase. Armor proficiency (`ArmorProficiency`, used only by `checkOrSave`'s disadvantage-on-skill-check rule) is untouched and still DnD5e-flavored, since it's unrelated to combat resolution.

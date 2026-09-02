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

Max HP and max mana follow a retail-accurate **per-class quadratic curve in level**
(`hpBase + hpAdd*level + hpMod*level²`, resp. `mpBase`/`mpAdd`/`mpMod`), taken directly from
the official Human Fighter/Human Mystic base HP/MP tables (fitted exactly against the level
1-20 retail values), then multiplied by `statBonus(CON)`/`statBonus(MEN)` exactly like
p.def/m.def — CON/MEN modulate the class curve as a build choice, same relationship as
CON→P.Def and MEN→M.Def, just applied to the vitals pool instead of a defense stat.

| Stat | Formula | Driven by |
|---|---|---|
| Max HP | `(classHpBase + classHpAdd*level + classHpMod*level²) * statBonus(CON)` | class curve (`hpBase`/`hpAdd`/`hpMod`), level, CON |
| Max Mana | `(classMpBase + classMpAdd*level + classMpMod*level²) * statBonus(MEN)` | class curve (`mpBase`/`mpAdd`/`mpMod`), level, MEN |
| HP regen/tick (3s) | `maxHealth * HP_REGEN_RATE * statBonus(CON)` | max HP, CON |
| Mana regen/tick (3s) | `maxMana * MP_REGEN_RATE * statBonus(MEN)` | max mana, MEN |

The tick period is **3 seconds** (`RegenHealthEngine`/`RegenManaEngine`'s `TICK_INTERVAL_MS`), matching retail L2J's `HpTask`/`MpTask` period — not an arbitrary rate. Passive regen without sitting/buffs therefore takes roughly `50 / statBonus(CON or MEN)` ticks (~2.5 minutes at neutral CON/MEN) to refill from empty, in line with the retail pace.

The 6 per-class curve coefficients live in `data/class.json` (`CharacterClass.maxHealth`/
`maxMana`, backed by `CombatFormulas.maxHealth`/`maxMana`) — no more DnD5e `hitDie`/
`manaGainPerLevel`/`primaryAbility` fields, all three were removed as dead SRD leftovers once
this curve replaced them. Unlike p.atk/p.def, `maxHealth`/`maxMana` stay **persisted fields**
(`CharacterInstance`, recomputed at character creation, level-up, and on every DB load in
`CharacterDao.toDomain`) rather than recomputed on every read: CON/MEN never change after
character creation in this project (no stat allocation, no attribute-granting gear, no passive
skill grants one yet either), so recomputing only at those three points is equivalent to a
fully derived stat, without the larger refactor a truly always-derived HP/mana would require
(see `CombatFormulas.maxHealth`/`maxMana`/`healthRegenPerTick`/`manaRegenPerTick`).

## Resolving a hit

Hit/evasion resolution is shared between a melee attack and a damage skill; the damage formula itself then diverges between physical and magic, each ported from L2J's `Formulas.calcPhysDam`/`calcMagicDam` (`net.sf.l2j.gameserver.skills.Formulas`) with the mechanics this project doesn't implement stripped out (soulshots/blessed-spiritshots, shield block, weapon-type/monster-race vulnerability stats, the optional magic-failure system, PvP damage bonuses):

1. `hitChance = clamp(0.80 + 0.02 * (accuracy - evasion), [0.20, 0.98])` (`CombatFormulas.hitChance`) — L2J's `calcHitMiss` stripped of its height/day-night/facing modifiers (no Z axis, no day/night cycle, no front/back facing in this project).
2. Roll `DiceRoller.rollChance(hitChance)`. Miss → 0 damage.
3. On a hit, roll critical independently: `DiceRoller.rollChance(criticalRate / 100.0)` — melee/`CombatSystem` and monster attacks use effective P.Crit; damage skills (`SkillSystem.rollDamage`) use effective P.Crit for a `SkillDamageType.PHYSICAL` skill, effective M.Crit for `MAGICAL`.
4. **Physical** (melee, or a `PHYSICAL` skill) — `CombatFormulas.resolvePhysicalDamage`: `damage = max(1, round(70 * atk / def * DiceRoller.randomVariance(0.9, 1.1) * (critical ? 2.0 : 1.0)))`. A skill's `power` (a flat, per-tier integer from `data/skills/skills.xml`) is added to the caster's effective P.Atk before this ratio, so skill tiers of the same name still scale meaningfully instead of all hitting identically; melee has no skill, so `atk` is just effective P.Atk.
5. **Magic** (a `MAGICAL` skill only — melee is never magic) — `CombatFormulas.resolveMagicalDamage`: `damage = max(1, round(91 * sqrt(mAtk) / mDef * power(level) * (critical ? 4.0 : 1.0)))`. Unlike physical, `power` is a *multiplicative* factor of the ratio (not added to M.Atk), a magic critical multiplies by 4 instead of 2, and there is no random variance — all three quirks are carried over as-is from the L2J source rather than smoothed into the physical shape.

## Elemental resistance

A damage skill whose `Skill.element()` (`SkillElement`: FIRE/WATER/WIND/EARTH/HOLY/DARK, or NONE for a physical/neutral skill) is not `NONE` has `CombatFormulas.applyElementalResistance(rawDamage, resistScore)` applied on top of step 4/5 above, in `SkillSystem.rollDamage`: `multiplier = clamp(1 - resistScore * 0.01, [0.1, 2.0])`, `damage = max(1, round(rawDamage * multiplier))`. `resistScore` reads `target.getElementalResistance(element)` — a positive score (resistance) reduces damage down to ×0.1, a negative one (vulnerability) amplifies it up to ×2. It sums `ItemTemplate.elementalResistances`/`MonsterTemplate.elementalResistances` (see `docs/lineage2/equipment.md`) across a character's equipped items, or reads a monster's natural resistances directly — never persisted, computed on the fly like every other derived stat. Melee/`CombatSystem` damage is untouched: elemental resistance only applies to skill damage, since melee has no element.

## Healing

A `SkillEffectType.HEALING` skill (`SkillSystem.castHeal`) heals the skill's actual target (not always the caster) for `power(level) + sqrt(2 * casterMAtk)` (`CombatFormulas.resolveHeal`), clamped to the target's missing HP by `AbstractCharacter.heal`. Ported from L2J's `Heal` skill handler (`net.sf.l2j...handler.skillhandlers.Heal`) with soulshot/blessed-spiritshot bonuses, the `HEAL_PROFICIENCY`/`HEAL_EFFECTIVNESS` stats, and the `HEAL_STATIC`/`HEAL_PERCENT` skill-type variants removed (this project has none of them; the `mAtkMul` the original computes always collapses to `2` once shots are gone) — a heal is never resisted, there's no defense-stat mitigation.

## Buffs/debuffs

`ActiveEffect.category()` derives `BUFF`/`DEBUFF` (`EffectCategory`) from `amount`'s sign — no separate persisted field. `EffectsSystem` caps active effects per category: `MAX_BUFF_SLOTS` (6), `MAX_DEBUFF_SLOTS` (4). Applying a new effect (not a refresh of an already-active `skillId`) while a category is full evicts the soonest-to-expire effect of that same category; `SkillSystem.castModifier` publishes `CharacterEffectExpired` for the evicted effect, reusing the exact same expiry pipeline (`ActiveEffectEngine`/`ActiveEffectPersistenceListener`) a natural timeout goes through — no new event type needed.

A debuff can additionally be resisted independently of the skill's normal hit/evasion check: `CombatFormulas.debuffResistChance(menScore)` — `clamp(0, 70, round(5 + statBonus(MEN) * 3.0)) / 100.0` — rolled right after a successful hit in `SkillSystem.castModifier`. A resist is reported the same way as a miss (`CastOutcome.hit() == false`) — there's no separate "resisted" status on the wire yet.

## What didn't change

Buff/debuff skills (`ModifiedStat.PATK/PDEF/MATK/MDEF/ACCURACY/EVASION`), heals, cast time, per-skill cooldown, and the melee attack cooldown (`CombatSystem.ATTACK_COOLDOWN`, 2s) are untouched — only the hit/damage resolution formula moved from DnD5e (d20 + modifier vs AC) to this ratio-based model. Peace zones still block all combat the same way (`AbstractZone`/`PeaceZone`).

## Removed: weapon proficiency

The DnD5e-era `WeaponCategory` (SIMPLE/MARTIAL) and the associated "non-proficient weapon" penalty were removed outright — Lineage2 has no equivalent concept, and there was no other use for it in the codebase. Armor proficiency (`ArmorProficiency`, used only by `checkOrSave`'s disadvantage-on-skill-check rule) is untouched and still DnD5e-flavored, since it's unrelated to combat resolution.

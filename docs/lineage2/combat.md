# Combat stats & resolution

Since 2026-10-11 every derived stat is the **Lineage 2 High Five** one: the stat functions of the L2JMobius H5 server (`model/stats/functions/formulas/Func*`, `Formulas.calcHpRegen/calcMpRegen`, `FuncEnchant`) applied to the H5 datapack base values — class templates (`stats/chars/baseStats`, copied into `data/classes/*.xml`), items (`stats/items`, copied into `data/items/*.xml` by the client repo's `tools/item_gen/build_items.py`) and NPCs (`stats/npcs`, copied into `data/monsters.xml` by `tools/monster_gen/build_monsters.py` and into `data/summons/summons.xml`). A character, a monster or a summon therefore shows the same numbers as in L2 (level-1 Human Fighter: 126 HP, P.Def 72, M.Def 47, accuracy/evasion 34, critical 88, atk.spd 417, speed 126). Implementation: `app.game.combat.CombatFormulas` (pure functions, no RNG) plus the RNG orchestration in `CombatSystem.attack`, `SkillSystem.rollDamage`/`castModifier` and `DamageModifiers`.

## Derived combat stats

`CombatFormulas.stats(BaseValues, attributes, level)` turns base values into the stat map of `StatSystem`; `levelMod = (level + 89) / 100` (0.90 at level 1, 1.29 at 40), `bonus(ATTR)` = `Attribute.bonus` (L2J `statBonus.xml`).

| Stat | Formula | Base value (player / NPC) |
|---|---|---|
| P.Atk | `base * bonus(STR) * levelMod` | weapon P.Atk, else class fist (4 fighter, 3 mystic) / NPC `pAtk` |
| M.Atk | `base * bonus(INT)² * levelMod²` | weapon M.Atk, else 6 / NPC `mAtk` |
| P.Def | `base * levelMod` (no CON) | worn pieces + bare defense of each empty slot (fighter chest 31, legs 18, head 12, feet 7, gloves 8, + underwear 3 and cloak 1 which have no slot here; mystic chest 15, legs 8); a full armor (`fullArmor`) also covers the legs; the shield adds nothing / NPC `pDef` |
| M.Def | `base * bonus(MEN) * levelMod` | worn jewels + bare defense of each empty jewel slot (earrings 9, rings 5, necklace 13) / NPC `mDef` |
| Accuracy | `sqrt(DEX)*6 + level + bonus` (+ L2 steps past level 69) | weapon type `accCombat` (`accuracyBonus`: blunt +5, dagger/bow/pole -4) / NPC `accuracy` |
| Evasion | `sqrt(DEX)*6 + level + bonus` | shield -8 / NPC `evasion` |
| P.Crit | `base * bonus(DEX) * 10`, ‰, max 500 | weapon base crit (`critBonus`: 8 sword/pole/dual, 12 dagger/bow, 4 blunt), else 4 / NPC `critical` |
| M.Crit | `(int)(1 * bonus(WIT) * 10) * 10`, ‰, max 200 (WIT counts only weapon in hand for a player) | — |
| Atk.Spd | `base * bonus(DEX)`; delay = `500000 / atkSpd` ms | weapon (379 one-hand, 433 dagger, 325 two-hand, 293 bow), else 300 / NPC `attackSpeed` |
| Cast.Spd | `333 * bonus(WIT)` | — |
| Speed | `base * bonus(DEX)` (L2 run speed; `MovementEngine.SPEED_DIVISOR` = 26 L2 units per grid cell per second) | class run speed (115 fighter, 120 mystic) / NPC `run` |

Critical rates are per mille like in L2 (`CombatFormulas.critChance`: 88 = 8.8 %). Each stat's effective value (`StatSystem.getEffective`) adds set bonuses, buffs/debuffs and passive skills (additive first, then multiplicative, both after the L2 functions, like L2J's `add`/`mul` funcs of order 0x40/0x30).

## Health & Mana pools

| Stat | Formula | Driven by |
|---|---|---|
| Max HP | `vitals(level).hp * bonus(CON)` | per-level table of the current class (`<vitals>` of `data/classes/<class>.xml`, L2 `lvlUpgainData`) |
| Max Mana | `vitals(level).mp * bonus(MEN)` + robe/jewel `maxMp` | same table, MEN, equipment |
| HP regen/tick (3s) | `vitals(level).hpRegen * levelMod * bonus(CON) * activity` | class table, level, CON, activity |
| Mana regen/tick (3s) | `vitals(level).mpRegen * levelMod * bonus(MEN) * activity` | class table, level, MEN, activity |

`activity` is L2's: ×1.5 resting (Relax), ×1.1 standing still, ×0.7 moving. The table is the one of the current profession (`ClassSystem.vitals`): a profession's table follows its parents' up to its access level then grows faster (Warrior level 40: 1044 base HP vs 637 for a Human Fighter without profession), so max HP/MP are recomputed (`ClassSystem.refreshVitals`) on level-up and class change, and on every DB load (`CharacterDao.toDomain`). Monsters use their datapack HP times CON (`CombatFormulas.maxHealth`) and do not regenerate.

## Resolving a hit

Hit/evasion resolution is shared between a melee attack and a damage skill; the damage formula itself then diverges between physical and magic, each ported from L2J's `Formulas.calcPhysDam`/`calcMagicDam` (`net.sf.l2j.gameserver.skills.Formulas`) with the mechanics this project doesn't implement stripped out (soulshots/blessed-spiritshots, shield block, weapon-type/monster-race vulnerability stats, the optional magic-failure system, PvP damage bonuses):

1. `hitChance = clamp(0.80 + 0.02 * (accuracy - evasion), [0.20, 0.98])` (`CombatFormulas.hitChance`) — L2J's `calcHitMiss` stripped of its height/day-night/facing modifiers (no Z axis, no day/night cycle, no front/back facing in this project).
2. Roll `Randomizer.rollChance(hitChance)`. Miss → 0 damage.
3. On a hit, roll critical independently: `Randomizer.rollChance(criticalRate / 1000.0)` (per mille) — melee/`CombatSystem` and monster attacks use effective P.Crit; damage skills (`SkillSystem.rollDamage`) use effective P.Crit for a `SkillDamageType.PHYSICAL` skill, effective M.Crit for `MAGICAL`.
4. **Physical** (melee, or a `PHYSICAL` skill) — `CombatFormulas.resolvePhysicalDamage`: `damage = max(1, round(70 * atk / def * Randomizer.randomVariance(0.9, 1.1) * (critical ? 2.0 : 1.0)))`. A skill's `power` (a flat, per-tier integer from `data/skills/skills.xml`) is added to the caster's effective P.Atk before this ratio, so skill tiers of the same name still scale meaningfully instead of all hitting identically; melee has no skill, so `atk` is just effective P.Atk.
5. **Magic** (a `MAGICAL` skill only — melee is never magic) — `CombatFormulas.resolveMagicalDamage`: `damage = max(1, round(91 * sqrt(mAtk) / mDef * power(level) * (critical ? 4.0 : 1.0)))`. Unlike physical, `power` is a *multiplicative* factor of the ratio (not added to M.Atk), a magic critical multiplies by 4 instead of 2, and there is no random variance — all three quirks are carried over as-is from the L2J source rather than smoothed into the physical shape.

## Elemental resistance

A damage skill whose `Skill.element()` (`SkillElement`: FIRE/WATER/WIND/EARTH/HOLY/DARK, or NONE for a physical/neutral skill) is not `NONE` has `CombatFormulas.applyElementalResistance(rawDamage, resistScore)` applied on top of step 4/5 above, in `SkillSystem.rollDamage`: `multiplier = clamp(1 - resistScore * 0.01, [0.1, 2.0])`, `damage = max(1, round(rawDamage * multiplier))`. `resistScore` reads `target.getElementalResistance(element)` — a positive score (resistance) reduces damage down to ×0.1, a negative one (vulnerability) amplifies it up to ×2. It sums `ItemTemplate.elementalResistances`/`MonsterTemplate.elementalResistances` (see `docs/lineage2/equipment.md`) across a character's equipped items, or reads a monster's natural resistances directly — never persisted, computed on the fly like every other derived stat. Melee/`CombatSystem` damage is untouched: elemental resistance only applies to skill damage, since melee has no element.

## Healing

A `SkillEffectType.HEALING` skill (`SkillSystem.castHeal`) heals the skill's actual target (not always the caster) for `power(level) + sqrt(2 * casterMAtk)` (`CombatFormulas.resolveHeal`), clamped to the target's missing HP by `AbstractCharacter.heal`. Ported from L2J's `Heal` skill handler (`net.sf.l2j...handler.skillhandlers.Heal`) with soulshot/blessed-spiritshot bonuses, the `HEAL_PROFICIENCY`/`HEAL_EFFECTIVNESS` stats, and the `HEAL_STATIC`/`HEAL_PERCENT` skill-type variants removed (this project has none of them; the `mAtkMul` the original computes always collapses to `2` once shots are gone) — a heal is never resisted, there's no defense-stat mitigation.

## Buffs/debuffs

`ActiveEffect.category()` derives `BUFF`/`DEBUFF` (`EffectCategory`) from `amount`'s sign — no separate persisted field. `EffectsSystem` caps active effects per category: `MAX_BUFF_SLOTS` (6), `MAX_DEBUFF_SLOTS` (4). Applying a new effect (not a refresh of an already-active `skillId`) while a category is full evicts the soonest-to-expire effect of that same category; `SkillSystem.castModifier` publishes `CharacterEffectExpired` for the evicted effect, reusing the exact same expiry pipeline (`ActiveEffectEngine`/`ActiveEffectPersistenceListener`) a natural timeout goes through — no new event type needed.

A debuff can additionally be resisted independently of the skill's normal hit/evasion check: `CombatFormulas.debuffResistChance(menScore)` — `clamp(0, 70, round(5 + bonus(MEN) * 3.0)) / 100.0` — rolled right after a successful hit in `SkillSystem.castModifier`. A resist is reported the same way as a miss (`CastOutcome.hit() == false`) — there's no separate "resisted" status on the wire yet.

## What didn't change

Buff/debuff skills (`ModifiedStat.PATK/PDEF/MATK/MDEF/ACCURACY/EVASION`), heals, cast time, per-skill cooldown, and the melee attack cooldown (`CombatSystem.ATTACK_COOLDOWN`, 2s) are untouched — only the hit/damage resolution formula moved from DnD5e (d20 + modifier vs AC) to this ratio-based model. Peace zones still block all combat the same way (`AbstractZone`/`PeaceZone`).

## Removed: weapon proficiency

The DnD5e-era `WeaponCategory` (SIMPLE/MARTIAL) and the associated "non-proficient weapon" penalty were removed outright — Lineage2 has no equivalent concept, and there was no other use for it in the codebase. Armor proficiency (`ArmorProficiency`, used only by `checkOrSave`'s disadvantage-on-skill-check rule) is untouched and still DnD5e-flavored, since it's unrelated to combat resolution.

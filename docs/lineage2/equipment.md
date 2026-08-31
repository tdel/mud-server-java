# Equipment: Item Combat Stats

`domain.item.ItemTemplate` (backed by `data/items.json`) carries the fields that feed the derived combat stats in [combat.md](combat.md):

| Field | Applies to | Feeds into |
|---|---|---|
| `pAtk` | weapons | `basePAtk()` on the wielder (physical attack) |
| `mAtk` | weapons | `baseMAtk()` on the wielder (magical attack / spell power) |
| `pDef` | armor, shields | summed into `basePDefSum()` across all equipped slots |
| `mDef` | armor, shields | summed into `baseMDefSum()` across all equipped slots |
| `accuracyBonus` | any equippable | summed into `accuracyItemBonus()` |
| `evasionBonus` | any equippable | summed into `evasionItemBonus()` |
| `critBonus` | any equippable | summed into `critItemBonus()` |
| `armorCategory` (LIGHT/MEDIUM/HEAVY) | armor (CHEST slot only) | `CombatFormulas.armorWeightPenalty()` — subtracted from evasion (0 / -4 / -10) |
| `elementalResistances` (`Map<SpellElement, Integer>`) | any equippable | summed into `elementalResistanceMap()` across all equipped slots, read via `getElementalResistance(element)` — see "Elemental resistance" in combat.md |
| `grade` (NOGRADE/D/C/B/A/S, `ItemGrade`) | any item | display/progression tier only, no combat formula reads it directly. `NOGRADE` marks non-equipment items (potions, food); equippable gear starts at `D`. Defaults to `NOGRADE` when absent from JSON. |
| `enchant` (0-20, per **instance**, not template) | weapons/armor | `CombatFormulas.enchantBonus(baseStat, enchant, bonusPerLevel)` — applied in `Item.getPAtk()/getMAtk()/getPDef()/getMDef()`, +2/level for atk stats, +1/level for def stats. Only applies to a stat the item already has (`baseStat > 0`), so enchanting an armor piece never grants it phantom P.Atk. Persisted on the `item` DB row (`V23__add_item_enchant.sql`), unlike every other stat above which lives on the file-based template. |
| `setId` (nullable `String`) | any equippable | groups items into a named set (`domain.item.ItemSet`, `data/item_sets.json`, `ItemSetCatalog`/`ItemSetCatalogHolder`). `CharacterInstance.setBonusModifiers()` counts equipped pieces sharing a `setId` and sums every tier in `ItemSet.bonusByPieceCount()` whose piece-count threshold is met (cumulative across tiers), added into the same `getEffectiveXxx()` stats as active-effect buffs. |

These replace the old DnD5e-era `baseAc` (armor class contribution), `damageDice` (weapon damage die, e.g. `"1d6"`), `weaponCategory` (SIMPLE/MARTIAL, removed — see combat.md), and the generic magic `bonus` field (which was applied inconsistently: only to armor/shield AC, never to a weapon's attack roll or damage). Every weapon usable by a Mystic — including its non-"magic" starting staff — carries a non-zero `mAtk`, since `mAtk` here means "magical channeling power of this weapon", not "this weapon has a magic enchantment"; otherwise a Mystic could never deal spell damage without a narratively-magic item.

`price`, `weight`, `type`/`EquipmentSlot`, and `grantedSpells` are unchanged. There is no rarity system — `grade` is the only tier concept.

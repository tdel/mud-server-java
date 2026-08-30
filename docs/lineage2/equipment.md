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

These replace the old DnD5e-era `baseAc` (armor class contribution), `damageDice` (weapon damage die, e.g. `"1d6"`), `weaponCategory` (SIMPLE/MARTIAL, removed — see combat.md), and the generic magic `bonus` field (which was applied inconsistently: only to armor/shield AC, never to a weapon's attack roll or damage). Every weapon usable by a Mystic — including its non-"magic" starting staff — carries a non-zero `mAtk`, since `mAtk` here means "magical channeling power of this weapon", not "this weapon has a magic enchantment"; otherwise a Mystic could never deal spell damage without a narratively-magic item.

`price`, `rarity`, `weight`, `type`/`EquipmentSlot`, and `grantedSpells` are unchanged.

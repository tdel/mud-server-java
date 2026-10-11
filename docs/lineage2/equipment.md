# Equipment: Item Combat Stats

`domain.item.ItemTemplate` (backed by `data/items.json`) carries the fields that feed the derived combat stats in [combat.md](combat.md):

| Field | Applies to | Feeds into |
|---|---|---|
| `pAtk` | weapons | the wielder's base P.Atk (replaces the class fist value) |
| `mAtk` | weapons (every L2 weapon has one) | the wielder's base M.Atk |
| `pDef` | armor pieces; shields | armor: replaces the class bare defense of its slot (`InventorySystem.computeBaseStats`); shield: L2 `sDef`, added to P.Def only on a successful block (`DamageModifiers.defenseWithBlock`, 20 % × DEX bonus) |
| `mDef` | jewels | replaces the class bare M.Def of its jewel slot |
| `accuracyBonus` | weapons (L2 `accCombat` of the weapon type, rounded: blunt +5, dagger/bow/pole -4) | added to accuracy |
| `evasionBonus` | shields (-8) | added to evasion |
| `critBonus` | weapons: L2 base critical (8 sword/pole/dual, 12 dagger/bow, 4 blunt) replacing the class one; other items: flat ‰ bonus | P.Crit |
| `armorCategory` (HEAVY/LIGHT/ROBE, the Lineage 2 armor types: heavy, light/leather, robe) | armor (the CHEST piece decides) | the condition of armor-dependent passives (`armor="..."` / `notArmor="..."` on a passive `<apply>`: Spellcraft and Magician's Movement penalize anything but a robe, bare chest included, as in L2J; Mana Recovery, Armor Mastery evasion) — new mystics start in an Apprentice's Robe (`StartingEquipmentEngine`) |
| `maxMp` | robes, some jewels | flat max MP bonus (base `MAX_MP`, added by `ResourceSystem.getMaxMana`) |
| `fullArmor` | one-piece chest armors | also covers the legs slot: its P.Def replaces both bare defenses; equipping it unequips the legs and vice versa |
| `elementalResistances` (`Map<SpellElement, Integer>`) | any equippable | summed into `elementalResistanceMap()` across all equipped slots, read via `getElementalResistance(element)` — see "Elemental resistance" in combat.md |
| `grade` (NOGRADE/D/C/B/A/S, `ItemGrade`) | any item | display/progression tier only, no combat formula reads it directly. `NOGRADE` marks non-equipment items (potions, food); equippable gear starts at `D`. Defaults to `NOGRADE` when absent from JSON. |
| `enchant` (0-20, per **instance**, not template) | weapons/armor | L2J `FuncEnchant` rules (`CombatFormulas.enchantPAtk/enchantMAtk/enchantDefense`, used by `Item.getPAtk()/getMAtk()/getPDef()/getMDef()`): weapon P.Atk +2..+10 per level depending on grade, two hands and bow, M.Atk +2/+3/+4, defense +1 per level, each level past +3 counting double (+3 for defense). Only applies to a stat the item already has. Persisted on the `item` DB row (`V23__add_item_enchant.sql`). |
| `setId` (nullable `String`) | any equippable | groups items into a named set (`domain.item.ItemSet`, `data/item_sets.json`, `ItemSetCatalog`/`ItemSetCatalogHolder`). `PlayerInstance.setBonusModifiers()` counts equipped pieces sharing a `setId` and sums every tier in `ItemSet.bonusByPieceCount()` whose piece-count threshold is met (cumulative across tiers), added into the same `getEffectiveXxx()` stats as active-effect buffs. |

These replace the old DnD5e-era `baseAc` (armor class contribution), `damageDice` (weapon damage die, e.g. `"1d6"`), `weaponCategory` (SIMPLE/MARTIAL, removed — see combat.md), and the generic magic `bonus` field (which was applied inconsistently: only to armor/shield AC, never to a weapon's attack roll or damage). Every weapon usable by a Mystic — including its non-"magic" starting staff — carries a non-zero `mAtk`, since `mAtk` here means "magical channeling power of this weapon", not "this weapon has a magic enchantment"; otherwise a Mystic could never deal spell damage without a narratively-magic item.

`price`, `weight`, `type`/`EquipmentSlot`, and `grantedSpells` are unchanged. There is no rarity system — `grade` is the only tier concept.


All weapon/armor/jewel values (stats, grade via `<expect>`, weight in grams, price in adena) are the Lineage 2 High Five ones, written by the client repo's `tools/item_gen/build_items.py`: an item named like an L2 item is that item, the others take an L2 item of the same slot and grade (see the `<!-- L2 H5 : ... -->` comment in each entry); `X +N` items are the L2 item enchanted N times.

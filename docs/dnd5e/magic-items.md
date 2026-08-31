# Magic Items

Source: [D&D 5e SRD 5.1](https://5thsrd.org/gamemaster_rules/magic_items/) (CC-BY-4.0, Wizards of the Coast) + standard DMG rarity guidance (widely published, non-SRD but industry-standard).

## Rarity

Every magic item has a rarity, a rough proxy for power/value and how commonly it can be found or bought:

| Rarity | Typical gp value (DMG guidance) | Availability |
|---|---|---|
| Common | 50–100 | Buyable in most towns |
| Uncommon | 101–500 | Found in cities |
| Rare | 501–5,000 | Cities only, or rare finds |
| Very Rare | 5,001–50,000 | Very hard to find |
| Legendary | 50,001+ | Might only be sold in extraordinary places (e.g. a city on another plane) |
| Artifact | priceless / not for sale | Unique, campaign-defining items |

## Weapon/armor bonuses

The common "+1 / +2 / +3" weapon or armor is the simplest expression of a magic item: it adds its bonus to attack and damage rolls (weapons) or to AC (armor/shields), stacking with all other normal modifiers. A +1 item is typically Uncommon, +2 Rare, +3 Very Rare — though nothing in the rules *forces* that mapping; it's a design convention, not a hard rule.

## Attunement

- Some magic items require **attunement** before their magic properties (beyond mundane use) function — stated explicitly per item.
- A creature can be attuned to a maximum of **3 items** at once.
- Attuning takes a **short rest** (≥1 minute) spent focused solely on the item; some items add prerequisites (e.g. "requires attunement by a spellcaster").
- Attunement ends if: the item is more than 100 ft away for 24+ hours, the owner dies, another creature attunes to it, or the owner voluntarily ends it (also a short rest).

## Activation

Varies per item: some work passively once worn/wielded, others need a **command word**, a **charge expenditure**, or are entirely **consumable** (potions, spell scrolls — a scroll lets you cast its spell as though you knew it, if it's on your class's spell list).

## Notes for this project

**Rarity has been removed from the game entirely** — there is no `Rarity` enum/field anymore. The only tier concept left is `domain/item/ItemGrade` (`NOGRADE, D, C, B, A, S`), a display/progression tier unrelated to the SRD rarity table above — see "Notes for this project" in [../lineage2/equipment.md](../lineage2/equipment.md). Magic bonuses aren't a flat integer either; combat itself is the Lineage2-style model (p.atk/p.def/m.atk/m.def) described in [../lineage2/combat.md](../lineage2/combat.md), not the SRD attack/damage/AC bonuses described above.

**Attunement is not implemented** — there's no limit on how many bonus-carrying items a character can equip simultaneously beyond normal equip slots. This is a known gap versus full SRD compliance, not an oversight to silently work around.

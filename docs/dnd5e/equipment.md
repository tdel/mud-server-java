# Equipment: Weapons, Armor, Gear & Currency

Source: [D&D 5e SRD 5.1](https://5thsrd.org/adventuring/equipment/) (CC-BY-4.0, Wizards of the Coast)

## Currency

| Coin | Abbrev. | Value in cp |
|---|---|---|
| Copper | cp | 1 |
| Silver | sp | 10 |
| Electrum | ep | 50 |
| Gold | gp | 100 |
| Platinum | pp | 1,000 |

Gold is the standard unit of account (1 gp ≈ a bedroll, or 50 ft of good rope, or a goat; a skilled artisan earns ~1 gp/day). 50 coins weigh 1 lb.

## Weapons

- **Simple** (club, mace, dagger, sling, ...) — usable by most classes without penalty.
- **Martial** (longsword, greatsword, longbow, ...) — requires proficiency training; several classes lack it.
- **Melee** (range 5 ft) vs **Ranged** (attack at distance).

### Properties

| Property | Effect |
|---|---|
| Ammunition | Consumes ammo per attack; recover ~half after the fight |
| Finesse | Attack/damage can use STR or DEX, whichever is better |
| Heavy | Small creatures have disadvantage attacking with it |
| Light | Suited to two-weapon fighting |
| Loading | Only one shot per action/bonus action/reaction, regardless of attacks-per-turn |
| Reach | +5 ft reach |
| Thrown | Can be thrown for a ranged attack |
| Two-Handed | Requires both hands |
| Versatile | One- or two-handed; damage die increases two-handed |
| Special | Unique rules (lance, net, etc.) |

### Examples

| Weapon | Category | Damage | Cost | Properties |
|---|---|---|---|---|
| Dagger | Simple melee | 1d4 piercing | 2 gp | Finesse, light, thrown |
| Shortbow | Simple ranged | 1d6 piercing | 25 gp | Ammunition, two-handed |
| Longsword | Martial melee | 1d8 slashing | 15 gp | Versatile (1d10) |
| Longbow | Martial ranged | 1d8 piercing | 50 gp | Ammunition, heavy, two-handed |
| Greatsword | Martial melee | 2d6 slashing | 50 gp | Heavy, two-handed |

## Armor

| Category | DEX bonus to AC | Notes |
|---|---|---|
| Light | full DEX modifier | e.g. padded (AC 11, stealth disadvantage), leather (AC 11), studded leather (AC 12) |
| Medium | DEX modifier, max +2 | e.g. hide (AC 12) up to half plate (AC 15); several impose stealth disadvantage |
| Heavy | none (but no DEX penalty either) | e.g. chain mail (AC 16, STR 13 req.), plate (AC 18, STR 15 req.); all impose stealth disadvantage; unmet STR requirement costs -10 ft speed |

**Shields**: +2 AC; only one shield's bonus applies at a time.

## Adventuring gear

Non-weapon, non-armor equipment with rules-relevant behavior: e.g. **Healer's Kit** (stabilize a dying creature without a Medicine check), **Climber's Kit** (limits an accidental fall to 25 ft while secured), rope, torches, rations, spyglasses, etc. Most items are flavor/utility rather than combat math.

## Notes for this project

`domain/Item` + `data/items.json` back the item-template system; weapon/armor damage/AC values and the properties table above are the reference for keeping item templates SRD-accurate. Currency conversions matter directly for `PlayerInventory.trySpendGold`/gold-drop balancing — see also [magic-items.md](magic-items.md) for the rarity/bonus system layered on top of these base items.

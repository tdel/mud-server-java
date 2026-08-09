# Conditions

Source: [D&D 5e SRD 5.1](https://5thsrd.org/rules/conditions/) (CC-BY-4.0, Wizards of the Coast)

Conditions alter a creature's capabilities. Most are applied/removed by specific spells, class features, or rules (e.g. grappling); several conditions imply others.

| Condition | Effect |
|---|---|
| **Blinded** | Can't see, auto-fails sight-based checks. Attacks against it: advantage. Its attacks: disadvantage. |
| **Charmed** | Can't attack or target the charmer with harmful effects. Charmer has advantage on social checks against it. |
| **Deafened** | Can't hear, auto-fails hearing-based checks. |
| **Frightened** | Disadvantage on ability checks and attack rolls while the fear source is in sight; can't willingly move closer to it. |
| **Grappled** | Speed 0, no speed bonuses. Ends if the grappler is incapacitated or the creature is forcibly moved out of reach. |
| **Incapacitated** | Can't take actions or reactions. |
| **Invisible** | Can't be seen without magic/special sense; heavily obscured for hiding purposes; can still be found by noise/tracks. Attacks against it: disadvantage. Its attacks: advantage. |
| **Paralyzed** | Incapacitated, can't move/speak, auto-fails STR/DEX saves. Attacks against it: advantage, and any hit from within 5 ft is a critical hit. |
| **Petrified** | Turned to stone (10× weight, stops aging), incapacitated, can't move/speak, unaware of surroundings. Resistance to all damage; immune to poison and disease (existing poison/disease is suspended, not cured). |
| **Poisoned** | Disadvantage on attack rolls and ability checks. |
| **Prone** | Can only crawl unless it stands (costs half speed). Disadvantage on its attacks. Attacks against it: advantage from within 5 ft, disadvantage from farther away. |
| **Restrained** | Speed 0, no speed bonuses. Attacks against it: advantage. Its attacks: disadvantage. Disadvantage on DEX saves. |
| **Stunned** | Incapacitated, can't move, speaks only falteringly, auto-fails STR/DEX saves. Attacks against it: advantage. |
| **Unconscious** | Incapacitated, can't move/speak, unaware of surroundings, drops held items and falls prone. Auto-fails STR/DEX saves. Attacks against it: advantage, and any hit from within 5 ft is a critical hit. |

## Exhaustion

Tracked as a stacking level (not a single on/off condition). Effects are cumulative — a creature at level 3 also suffers levels 1 and 2:

| Level | Effect |
|---|---|
| 1 | Disadvantage on ability checks |
| 2 | Speed halved |
| 3 | Disadvantage on attack rolls and saving throws |
| 4 | Hit point maximum halved |
| 5 | Speed reduced to 0 |
| 6 | Death |

A long rest reduces exhaustion by 1 level, but only if the creature also eats and drinks. See [resting.md](resting.md).

## Notes for this project

None of these are implemented as a generic status-effect system yet — combat currently resolves via `GameMonster`/`GamePlayer` HP exchange without conditions. If conditions are added, this table is the canonical effect list to encode; grappled/restrained/prone interact directly with the hex-grid movement system (`domain/HexCoordinate`).

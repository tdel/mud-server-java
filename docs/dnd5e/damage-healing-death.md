# Damage, Healing & Death

Source: [D&D 5e SRD 5.1](https://5thsrd.org/combat/damage_and_healing/) (CC-BY-4.0, Wizards of the Coast)

## Hit points

HP represents durability, will to live, and luck combined. Losing HP has no mechanical penalty until it hits 0 — there's no scaling debuff as HP drops (unlike some other systems).

## Dealing damage

- Roll the attack/spell's damage dice + modifiers. A penalty can bring damage to 0, never negative.
- **Area effects** hitting multiple targets: roll damage once, apply the same roll to everyone caught in it.
- **Critical hit**: roll all damage dice *twice*, then add modifiers once.

## Damage types

`acid, bludgeoning, cold, fire, force, lightning, necrotic, piercing, poison, psychic, radiant, slashing, thunder` — 13 total.

- **Resistance**: halves damage of that type.
- **Vulnerability**: doubles damage of that type.
- Multiple sources of the same resistance/vulnerability don't stack — it's binary per type.

## Dropping to 0 HP

- **Instant death**: if the damage that reduces you to 0 has *leftover* damage ≥ your HP maximum, you die outright (no death saves).
- Otherwise: you fall unconscious, and stay unconscious until you regain any HP.

### Death saving throws

- At 0 HP with no instant death, roll `1d20` at the start of each of your turns (no modifiers): **10+ succeeds**, else fails.
- **3 successes** → stabilized (still unconscious at 0 HP until healed). **3 failures** → dead. Successes/failures don't need to be consecutive but resets on stabilizing/healing.
- Natural **1** counts as **two** failures. Natural **20** immediately regains **1 HP** (character wakes up).
- Taking any damage while at 0 HP = one death-save failure automatically (two on a crit); melee damage taken at 0 HP that isn't itself enough to kill still just counts as a failure unless it triggers the instant-death threshold above.

### Stabilizing

Action + DC 10 Wisdom (Medicine) check on an unconscious creature → stable (stops death saves, stays unconscious at 0 HP, no longer at risk without further damage). Stability is lost if the creature takes damage again.

### Knocking out instead of killing

A melee attacker reducing a target to 0 HP can choose to knock it out instead of killing it: target falls unconscious and stable.

## Temporary hit points

- A buffer absorbed before real HP; lost first on any damage, with overflow hitting real HP.
- Can exceed HP max. Don't stack with a new source (take the higher value, don't add). Last until depleted or until a long rest.
- Do **not** restore consciousness or stability at 0 HP.

## Healing

- Resting or magical healing (spells, potions) restores HP, capped at maximum.
- A dead creature cannot regain HP through normal healing — only revivify/resurrection-class magic works.

## Notes for this project

`GamePlayer.takeDamage` / `CharacterDied` / `GamePlayerDied` events map onto "instant death" and "0 HP" above, but the game currently has no unconsciousness/death-save state — death appears to be immediate at 0 HP. If death saves are ever added, this is the reference; potions of healing are already implemented (see the recent "rend les potions de soin utilisables" work) and should cap at HP max per the healing rule above.

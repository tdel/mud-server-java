# Lineage2-style Combat Reference

Reference documentation for the current combat/stats model, which replaced the DnD5e-based combat engine (attack roll + AC) with a Lineage2-style model (p.atk/p.def/m.atk/m.def, accuracy/evasion, criticalRate). See `CLAUDE.md` for the overall project scope: races/classes/leveling had already deviated from strict DnD5e compliance before this change; combat now deviates too.

These formulas are **not** an exact reproduction of Lineage2's retail client — NCSoft never published them. They are reconstructed from mechanics widely documented by the community (notably the open-source L2J server emulator), and the numeric constants are a starting point to balance-test, not a guaranteed byte-for-byte match. See `app.game.combat.CombatFormulas` for the authoritative implementation.

| File | Covers |
|---|---|
| [combat.md](combat.md) | Derived combat stats, hit/critical/damage resolution |
| [equipment.md](equipment.md) | Item fields backing combat stats (pAtk/mAtk/pDef/mDef/...) |

## What's still DnD5e

Skill/saving-throw checks (`CharacterInstance.check`/`save`/`checkOrSave`), the `(score-10)/2` ability modifier used for HP-on-level-up and those checks, short/long rest rules, and XP/leveling progression are unchanged — only the combat resolution itself (attack, spell damage, defense) moved to this model. See `docs/dnd5e/README.md` for those.

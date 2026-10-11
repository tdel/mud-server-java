# Lineage2-style Combat Reference

Reference documentation for the current combat/stats model, which replaced the DnD5e-based combat engine (attack roll + AC) with a Lineage2-style model (p.atk/p.def/m.atk/m.def, accuracy/evasion, criticalRate). See `CLAUDE.md` for the overall project scope: races/classes/leveling had already deviated from strict DnD5e compliance before this change; combat now deviates too.

NCSoft never published the retail formulas: the derived stats are a port of the open-source L2JMobius High Five server (stat functions) applied to the H5 datapack values (classes, items, NPCs), see [combat.md](combat.md); damage resolution keeps a simplified L2J `calcPhysDam`/`calcMagicDam`. See `app.game.combat.CombatFormulas` for the authoritative implementation.

| File | Covers |
|---|---|
| [combat.md](combat.md) | Derived combat stats, hit/critical/damage resolution |
| [equipment.md](equipment.md) | Item fields backing combat stats (pAtk/mAtk/pDef/mDef/...) |

## What's still DnD5e

Skill/saving-throw checks (`PlayerInstance.check`/`save`/`checkOrSave`), the `(score-10)/2` ability modifier those checks use, short/long rest rules, and XP/leveling progression are unchanged — only combat resolution (attack, skill damage, defense) and, since [combat.md](combat.md)'s "Health & Mana pools" section, max HP/mana and their regen moved to this model.

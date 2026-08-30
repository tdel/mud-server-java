-- Le catalogue de sorts DnD5e (data/spells.json) est remplacé par des skills
-- Lineage2 (nouveaux noms, nouveaux id, effectDice -> power). Les lignes
-- existantes de character_spell/character_active_effect référencent des
-- spell_id qui n'existent plus dans le catalogue rechargé au warm-up
-- (SpellCatalog.getById lèverait une exception au premier accès) : même
-- pattern que V21__lineage2_combat_stats.sql, on les vide plutôt que de les
-- remapper. SpellLearningEngine.reconcile() (appelé dans CharacterSelect)
-- réapprend automatiquement les bons sorts pour le niveau du personnage juste
-- après la reconnexion.
DELETE FROM character_active_effect;
DELETE FROM character_spell;

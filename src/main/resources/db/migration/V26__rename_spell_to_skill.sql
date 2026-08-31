-- Renomme la terminologie "Spell" en "Skill" dans le schéma, suite à la fusion
-- sorts/compétences passives sous SkillSystem (domain/actor/system/SkillSystem.java) :
-- un "sort" n'est plus qu'une compétence active parmi d'autres. Migration dédiée
-- (plutôt qu'une édition de V14/V15 en place) car ces deux tables sont déjà
-- appliquées sur les bases existantes — SQLite supporte RENAME TABLE/RENAME
-- COLUMN directement, pas besoin de reconstruire les tables.
ALTER TABLE character_spell RENAME TO character_skill;
ALTER TABLE character_skill RENAME COLUMN spell_id TO skill_id;

ALTER TABLE character_active_effect RENAME COLUMN spell_id TO skill_id;
ALTER TABLE character_active_effect RENAME COLUMN spell_name TO skill_name;

DROP INDEX idx_character_spell_character_id;
CREATE INDEX idx_character_skill_character_id ON character_skill (character_id);

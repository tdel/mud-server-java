-- Depuis que les compétences passives de type "Expertise Grade" (data/skills/skills.xml)
-- portent plusieurs levels (level -> grade débloqué, cf. PassiveSkill.gradeAt) au lieu
-- d'un skill distinct par grade, il faut mémoriser le level connu, pas seulement l'id.
ALTER TABLE character_passive_skill ADD COLUMN level INTEGER NOT NULL DEFAULT 1;

-- Compétences passives connues par un personnage (ex: "Expertise D-Grade"). Aucun
-- mécanisme d'apprentissage automatique pour l'instant (contrairement à
-- character_spell) : cette table n'est peuplée que manuellement/par un futur outil.
-- passive_skill_id référence un PassiveSkill chargé en mémoire depuis
-- data/skills/passives.json (PassiveSkillCatalog), pas une table dédiée — même
-- convention que character_spell.spell_id.
CREATE TABLE character_passive_skill (
    character_id UUID NOT NULL REFERENCES character(id),
    passive_skill_id UUID NOT NULL,
    PRIMARY KEY (character_id, passive_skill_id)
);

CREATE INDEX idx_character_passive_skill_character_id ON character_passive_skill (character_id);

-- Sorts appris par un personnage. Apprentissage automatique à la montée de niveau
-- (CharacterInstance.applyLevelUp()) ou à la création du personnage pour les sorts de
-- niveau 1 (WorldInstance.createCharacter()). spell_id référence un Spell chargé en
-- mémoire depuis data/spells.json (SpellCatalog), pas une table spell — même
-- convention que item.template_id (voir V1__init_schema.sql).
CREATE TABLE character_spell (
    character_id UUID NOT NULL REFERENCES character(id),
    spell_id     UUID NOT NULL,
    PRIMARY KEY (character_id, spell_id)
);

CREATE INDEX idx_character_spell_character_id ON character_spell (character_id);

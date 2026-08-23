-- Buffs/debuffs actifs (Bless, Bane, Shield of Faith...) avec leur expiration, pour
-- qu'un joueur retrouve ses effets en cours à la reconnexion. spell_id référence un
-- Spell chargé en mémoire depuis data/spells.json (SpellCatalog), pas une table
-- spell — même convention que character_spell.spell_id (V14) et item.template_id
-- (V1). Une ligne par sort actif : un recast du même sort met à jour la ligne
-- existante (delete puis insert côté DAO) au lieu d'empiler, comme en mémoire
-- (ActiveEffects, clé = spellId).
CREATE TABLE character_active_effect (
    character_id UUID NOT NULL REFERENCES character(id),
    spell_id     UUID NOT NULL,
    spell_name   VARCHAR(255) NOT NULL,
    stat         VARCHAR(20) NOT NULL,
    amount       INT NOT NULL,
    expires_at   TIMESTAMP NOT NULL,
    PRIMARY KEY (character_id, spell_id)
);

CREATE INDEX idx_character_active_effect_character_id ON character_active_effect (character_id);

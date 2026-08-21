-- Nombre de repos courts pris depuis le dernier repos long (voir
-- CharacterInstance.MAX_SHORT_RESTS_BEFORE_LONG_REST) : au-delà d'un certain
-- seuil, un repos long redevient obligatoire pour le réinitialiser.
ALTER TABLE character ADD COLUMN short_rest_count INT NOT NULL DEFAULT 0;

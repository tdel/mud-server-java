-- Mana max / mana actuelle pour le futur système de sorts. Pas de régénération
-- automatique pour l'instant : la mana ne remonte qu'à la montée de niveau
-- (CharacterInstance.applyLevelUp(), via CharacterClass.manaGainPerLevel()) ou en
-- buvant une potion de mana (ConsumableEffect.MANA_RESTORE).
ALTER TABLE character ADD COLUMN max_mana INT NOT NULL DEFAULT 0;
ALTER TABLE character ADD COLUMN current_mana INT NOT NULL DEFAULT 0;

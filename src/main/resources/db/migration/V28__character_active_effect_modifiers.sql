ALTER TABLE character_active_effect ADD COLUMN modifiers TEXT NOT NULL DEFAULT '[]';
ALTER TABLE character_active_effect DROP COLUMN stat;
ALTER TABLE character_active_effect DROP COLUMN amount;

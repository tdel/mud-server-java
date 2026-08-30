-- Le moteur de combat DnD5e (jet d20 + AC) est remplacé par un système
-- Lineage2-style (p.atk/p.def/m.atk/m.def/accuracy/evasion/criticalRate,
-- calculés à la volée, jamais persistés — comme armorClass avant elle). Les
-- attributs WISDOM/CHARISMA sont renommés WIT/MEN pour coller au vocabulaire
-- L2, et les personnages passent d'un tirage aléatoire à un profil de stats
-- fixe par classe : reset complet, même pattern que
-- V20__reset_characters_for_human_fighter_mystic.sql, pas de remapping.
DELETE FROM item WHERE character_id IN (SELECT id FROM character);
DELETE FROM character_spell WHERE character_id IN (SELECT id FROM character);
DELETE FROM character_active_effect WHERE character_id IN (SELECT id FROM character);

DELETE FROM character;

ALTER TABLE character RENAME COLUMN wisdom TO wit;
ALTER TABLE character RENAME COLUMN charisma TO men;

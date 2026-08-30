-- Les races/classes DnD5e SRD sont retirées au profit d'un système simplifié
-- (Race unique HUMAN, classes FIGHTER/MYSTIC + sous-classes niveau 20/40). Les
-- personnages existants référencent des races/classes qui n'existeront plus dans
-- Race.java/CharacterClass.java au prochain démarrage (Race.valueOf/CharacterClass
-- .valueOf échoueraient au chargement) : reset complet, pas de remapping (décision
-- produit, contexte dev sans contrainte de prod) — même ordre de suppression que
-- V5__remove_orc_characters.sql (items puis personnage).
DELETE FROM item WHERE character_id IN (SELECT id FROM character);
DELETE FROM character_spell WHERE character_id IN (SELECT id FROM character);
DELETE FROM character_active_effect WHERE character_id IN (SELECT id FROM character);

DELETE FROM character;

-- Sous-classe choisie au niveau 20 (tier 1) et au niveau 40 (tier 2, aucune option
-- disponible pour le moment côté application — voir Subclass.availableAt). Pure
-- étiquette pour l'instant, aucun effet mécanique.
ALTER TABLE character ADD COLUMN subclass_tier1 VARCHAR(20);
ALTER TABLE character ADD COLUMN subclass_tier2 VARCHAR(20);

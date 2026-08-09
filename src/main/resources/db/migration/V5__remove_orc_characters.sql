-- La race ORC n'est pas une race DnD5e SRD (le SRD a Half-Orc) et est retirée
-- de Race.java au profit de HALF_ORC. On supprime les personnages ORC restants
-- pour éviter un Race.valueOf("ORC") en échec au prochain chargement de leur
-- compte ; même ordre de détachement des FK que CharacterDelete.onReceive.
UPDATE account SET current_character_id = NULL
    WHERE current_character_id IN (SELECT id FROM character WHERE race = 'ORC');

DELETE FROM item WHERE character_id IN (SELECT id FROM character WHERE race = 'ORC');

DELETE FROM character WHERE race = 'ORC';

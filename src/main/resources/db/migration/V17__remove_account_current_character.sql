-- account.current_character_id est un reliquat mort de l'ancien design multi-world
-- (sélection de personnage par compte au login) : depuis le retour à un monde global
-- unique (V12__remove_multi_world.sql), CHARSELECT liste tous les personnages du compte
-- et plus aucun code applicatif ne lit cette colonne (seul un aller-retour set/unset
-- subsistait : WorldInstanceService.enterGame la fixait, CharacterDelete la remettait
-- à null).
--
-- ALTER TABLE ... DROP COLUMN fonctionne nativement sous SQLite, y compris sur une
-- colonne impliquée dans une FK (précédent : V11__remove_item_room.sql, V12).
ALTER TABLE account DROP COLUMN current_character_id;

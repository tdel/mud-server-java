-- Fait respecter en base la règle "1 personnage par (compte, WorldInstance)"
-- déjà appliquée côté application depuis la Phase C
-- (CharacterCreate/CharacterSelect, voir multi-world.md) — reportée jusqu'ici
-- faute de savoir si les données le permettraient encore une fois le Lobby en
-- place ; aucun doublon possible désormais, le contrôle applicatif l'empêche
-- déjà à la création.
ALTER TABLE character ADD CONSTRAINT uniq_character_account_world_instance UNIQUE (account_id, world_instance_id);

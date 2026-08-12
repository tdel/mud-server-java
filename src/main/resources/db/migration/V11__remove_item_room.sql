-- Un Item ne peut plus se trouver "au sol" dans une room : il vit uniquement
-- dans une loot table (data/monsters.json) ou dans l'inventaire d'un
-- personnage. room_id (et son index) deviennent inutiles.
DROP INDEX idx_item_room_id;
ALTER TABLE item DROP COLUMN room_id;

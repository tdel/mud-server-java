-- Renommage du concept "Room" en "Zone" (voir domain.world.ZoneInstance/ZoneTemplate).
-- SQLite supporte RENAME COLUMN nativement, pas de rebuild de table nécessaire.
ALTER TABLE character RENAME COLUMN current_room_id TO current_zone_id;

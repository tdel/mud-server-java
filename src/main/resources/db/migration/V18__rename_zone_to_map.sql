-- Renommage du concept "Zone" en "Map" (voir domain.world.MapInstance/MapTemplate).
ALTER TABLE character RENAME COLUMN current_zone_id TO current_map_id;

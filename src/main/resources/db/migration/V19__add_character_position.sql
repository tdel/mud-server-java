-- Position (x/y) des personnages, pour la sauvegarde périodique/immédiate de leur emplacement sur
-- la map. Colonnes nullables : à la création d'un personnage, la position n'est pas encore connue,
-- elle n'est fixée qu'au join sur la map.
ALTER TABLE character ADD COLUMN pos_x DOUBLE PRECISION;
ALTER TABLE character ADD COLUMN pos_y DOUBLE PRECISION;

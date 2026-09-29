-- Les attributs passent de l'ancienne échelle "10 = neutre" (héritée de DnD5e)
-- aux valeurs retail de Lineage 2 (datapack L2J High Five), avec leurs
-- multiplicateurs propres à chaque attribut (Attribute.bonus). Les attributs
-- sont fixés par la classe : remise à niveau des personnages existants. Les PV/PM
-- max sont recalculés au chargement (CharacterDao), qui plafonne aussi les PV/PM
-- courants.
UPDATE character SET strength = 40, dexterity = 30, constitution = 43, intelligence = 21, wit = 11, men = 25
    WHERE character_class = 'FIGHTER';
UPDATE character SET strength = 22, dexterity = 21, constitution = 27, intelligence = 41, wit = 20, men = 39
    WHERE character_class = 'MYSTIC';

-- Retour à un monde unique : suppression du lobby/party/sélection de World. Un seul
-- WorldInstance existe désormais, construite en mémoire au démarrage (id fixe
-- fr.idev.mudserver.domain.world.WorldInstance.DEFAULT_ID) et plus jamais persistée.
-- world_instance_member et character.world_instance_id n'ont donc plus aucun lecteur/
-- écrivain applicatif ; world_instance elle-même n'est plus lue qu'en mémoire.
--
-- Contrairement à V8 (qui devait *ajouter* une contrainte NOT NULL, impossible sans
-- rebuild de table sous SQLite), ici on ne fait que *retirer* une colonne/des tables :
-- ALTER TABLE ... DROP COLUMN et DROP TABLE fonctionnent nativement sous SQLite même
-- sur une colonne/table impliquée dans une FK (déjà le cas pour item.room_id en
-- V11__remove_item_room.sql), sans PRAGMA ni rebuild de table.
--
-- idx_character_world_instance_id a été créée par V8 à l'intérieur de son propre bloc
-- [jooq ignore] (nécessaire à l'époque pour le rebuild de table) : le simulateur H2 de
-- jOOQ ne l'a donc jamais vue exister et refuse de la DROP. Seul ce DROP INDEX est donc
-- lui aussi exclu du parseur jOOQ ; Flyway l'exécute normalement au runtime. Le reste
-- (DROP COLUMN, DROP TABLE) reste visible à jOOQ pour que le schéma généré perde bien
-- character.world_instance_id / world_instance / world_instance_member.
-- [jooq ignore start]
DROP INDEX idx_character_world_instance_id;
-- [jooq ignore stop]
ALTER TABLE character DROP COLUMN world_instance_id;

DROP TABLE world_instance_member;
DROP TABLE world_instance;

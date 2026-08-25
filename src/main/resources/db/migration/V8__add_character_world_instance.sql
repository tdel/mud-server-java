-- Rattache chaque personnage à la WorldInstance qui l'héberge. L'id de la
-- WorldInstance par défaut ci-dessous (créée pour ne rien perdre des personnages
-- déjà existants) doit rester synchronisé avec
-- app.domain.world.WorldInstance.DEFAULT_ID, et son world_template_id
-- avec l'id du monde "default" (voir data/worlds/default/world.json) :
-- littéraux fixes des deux côtés, pas générés à l'exécution, pour que ce
-- backfill reste déterministe et reproductible.
ALTER TABLE character ADD COLUMN world_instance_id UUID NULL REFERENCES world_instance(id);

INSERT INTO world_instance (id, world_template_id, party_leader_account_id, created_at)
VALUES ('a8e98a8e-73c1-43dd-b36e-a2f67f00ff48', 'f128833b-9a8a-4fb9-9796-33fd9413490d', NULL, CURRENT_TIMESTAMP);

-- Pas de cast ::uuid (syntaxe Postgres, invalide en SQLite) : les UUID sont de simples
-- littéraux texte pour SQLite (voir V1__init_schema.sql).
INSERT INTO world_instance_member (world_instance_id, account_id)
SELECT DISTINCT 'a8e98a8e-73c1-43dd-b36e-a2f67f00ff48', account_id FROM character;

UPDATE character SET world_instance_id = 'a8e98a8e-73c1-43dd-b36e-a2f67f00ff48' WHERE world_instance_id IS NULL;

-- SQLite ne supporte pas ALTER TABLE ... ALTER COLUMN ... SET NOT NULL (ni aucune
-- forme d'altération de contrainte de colonne) : on applique ici le rebuild de table
-- documenté par SQLite (créer la table cible, copier les données, remplacer
-- l'ancienne) pour fermer world_instance_id en NOT NULL. PRAGMA foreign_keys
-- désactivé le temps du rebuild car item.character_id référence cette table (sans
-- ça, le DROP TABLE ci-dessous déclenche un DELETE FROM implicite bloqué par la FK).
--
-- Tout le bloc est exclu du parseur de génération de code jOOQ ([jooq ignore] —
-- DDLDatabase simule via H2, qui ne comprend pas PRAGMA et refuse le DROP TABLE à
-- cause de la FK entrante d'item) ; Flyway l'exécute normalement au runtime, ce ne
-- sont que des commentaires SQL pour lui. Effet de bord pour la génération : la
-- classe jOOQ Character générée reste basée sur la forme juste avant ce bloc
-- (world_instance_id nullable, pas encore NOT NULL) — sans incidence, les DAO ne
-- dépendent pas de cette nullabilité côté généré.
-- [jooq ignore start]
PRAGMA foreign_keys = OFF;

CREATE TABLE character_new (
    id                UUID PRIMARY KEY,
    account_id        UUID NOT NULL REFERENCES account(id),
    name              VARCHAR(255) NOT NULL,
    current_room_id   UUID NOT NULL,
    gender            VARCHAR(10) NOT NULL,
    race              VARCHAR(20) NOT NULL,
    character_class   VARCHAR(20) NOT NULL,
    level             INT NOT NULL DEFAULT 1,
    current_health    INT NOT NULL,
    max_health        INT NOT NULL,
    strength          INT NOT NULL DEFAULT 10,
    dexterity         INT NOT NULL DEFAULT 10,
    constitution      INT NOT NULL DEFAULT 10,
    intelligence      INT NOT NULL DEFAULT 10,
    wisdom            INT NOT NULL DEFAULT 10,
    charisma          INT NOT NULL DEFAULT 10,
    xp                INT NOT NULL DEFAULT 0,
    gold              INT NOT NULL DEFAULT 0,
    short_rest_count  INT NOT NULL DEFAULT 0,
    world_instance_id UUID NOT NULL REFERENCES world_instance(id)
);

INSERT INTO character_new
SELECT id, account_id, name, current_room_id, gender, race, character_class, level,
       current_health, max_health, strength, dexterity, constitution, intelligence,
       wisdom, charisma, xp, gold, short_rest_count, world_instance_id
FROM character;

DROP TABLE character;
ALTER TABLE character_new RENAME TO character;

PRAGMA foreign_keys = ON;

-- idx_character_account_id (V4__add_fk_indexes.sql) est perdu avec le DROP TABLE
-- ci-dessus : recréé ici, avec le nouvel index sur world_instance_id. Ces deux CREATE
-- INDEX restent dans le bloc [jooq ignore] : côté simulation jOOQ, la table
-- "character" n'a jamais été droppée (voir plus haut), donc idx_character_account_id
-- y existe déjà et cette recréation entrerait en conflit.
CREATE INDEX idx_character_account_id ON character (account_id);
CREATE INDEX idx_character_world_instance_id ON character (world_instance_id);
-- [jooq ignore stop]

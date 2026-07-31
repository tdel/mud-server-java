-- Schéma initial, portage du modèle Doctrine du projet PHP (5 migrations incrémentales
-- côté PHP, consolidées ici en une seule puisqu'il s'agit d'un schéma neuf).
-- UUID générés côté application (pas de DEFAULT gen_random_uuid()), comme en PHP.

CREATE TABLE item_template (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT NULL,
    type        VARCHAR(50) NOT NULL,
    weight      INT NOT NULL
);

CREATE TABLE room (
    id               UUID PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    description      TEXT NOT NULL,
    is_starting_room BOOLEAN NULL
);

-- Postgres traite chaque NULL comme distinct dans un index unique : cette contrainte
-- rejette une deuxième ligne à TRUE tout en autorisant un nombre illimité de NULL.
-- C'est le mécanisme qui impose "une seule salle de départ" au niveau DB. Ne pas
-- "corriger" en pensant que c'est un bug.
CREATE UNIQUE INDEX uniq_room_starting ON room (is_starting_room);

-- account.current_character_id et character.account_id se référencent mutuellement ;
-- account est créée sans la FK vers character, ajoutée après coup une fois la table
-- character en place (même contournement que les migrations Doctrine côté PHP).
CREATE TABLE account (
    id                    UUID PRIMARY KEY,
    login                 VARCHAR(255) NOT NULL UNIQUE,
    password              VARCHAR(255) NOT NULL,
    current_character_id  UUID NULL
);

CREATE TABLE character (
    id             UUID PRIMARY KEY,
    account_id     UUID NOT NULL REFERENCES account(id),
    name           VARCHAR(255) NOT NULL,
    current_room_id UUID NOT NULL REFERENCES room(id),
    race           VARCHAR(20) NOT NULL,
    current_health INT NOT NULL,
    max_health     INT NOT NULL,
    current_mana   INT NOT NULL DEFAULT 10,
    max_mana       INT NOT NULL DEFAULT 10,
    strength       INT NOT NULL DEFAULT 10,
    dexterity      INT NOT NULL DEFAULT 10,
    constitution   INT NOT NULL DEFAULT 10,
    intelligence   INT NOT NULL DEFAULT 10,
    wisdom         INT NOT NULL DEFAULT 10,
    charisma       INT NOT NULL DEFAULT 10
);

ALTER TABLE account
    ADD CONSTRAINT fk_account_current_character
    FOREIGN KEY (current_character_id) REFERENCES character(id);

-- Sens unique (source -> target) ; un chemin retour nécessite sa propre ligne.
CREATE TABLE room_exit (
    id             UUID PRIMARY KEY,
    direction      VARCHAR(255) NOT NULL,
    source_room_id UUID NOT NULL REFERENCES room(id),
    target_room_id UUID NOT NULL REFERENCES room(id)
);

CREATE TABLE item (
    id           UUID PRIMARY KEY,
    template_id  UUID NOT NULL REFERENCES item_template(id),
    room_id      UUID NULL REFERENCES room(id),
    character_id UUID NULL REFERENCES character(id),
    slot         VARCHAR(20) NULL
);

-- DEFERRABLE INITIALLY DEFERRED : le slot-swap d'equipItem fait deux UPDATE dans la
-- même transaction (déséquiper l'ancien occupant, équiper le nouveau) ; une contrainte
-- non différée échouerait sur l'état transitoire entre les deux.
ALTER TABLE item
    ADD CONSTRAINT uniq_character_slot UNIQUE (character_id, slot) DEFERRABLE INITIALLY DEFERRED;

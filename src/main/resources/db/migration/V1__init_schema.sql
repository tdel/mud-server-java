-- Schéma initial, portage du modèle Doctrine du projet PHP (5 migrations incrémentales
-- côté PHP, consolidées ici en une seule puisqu'il s'agit d'un schéma neuf).
-- UUID générés côté application (pas de DEFAULT gen_random_uuid()), comme en PHP.

-- account.current_character_id et character.account_id se référencent mutuellement ;
-- account est créée sans la FK vers character, ajoutée après coup une fois la table
-- character en place (même contournement que les migrations Doctrine côté PHP).
CREATE TABLE account (
    id                    UUID PRIMARY KEY,
    login                 VARCHAR(255) NOT NULL UNIQUE,
    password              VARCHAR(255) NOT NULL,
    current_character_id  UUID NULL
);

-- current_room_id n'est plus une FK vers une table room : les Room sont chargées
-- en mémoire depuis data/rooms.json (voir RoomService.warmRooms()), pas persistées
-- en DB. Validé côté application, comme character.race.
CREATE TABLE character (
    id             UUID PRIMARY KEY,
    account_id     UUID NOT NULL REFERENCES account(id),
    name           VARCHAR(255) NOT NULL,
    current_room_id UUID NOT NULL,
    race           VARCHAR(20) NOT NULL,
    character_class VARCHAR(20) NOT NULL,
    level          INT NOT NULL DEFAULT 1,
    current_health INT NOT NULL,
    max_health     INT NOT NULL,
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

-- template_id n'est plus une FK vers une table item_template : les ItemTemplate sont
-- chargés en mémoire depuis data/items.json (voir ItemService.warmItemTemplates()),
-- pas persistés en DB. Validé côté application, comme character.race. room_id n'est
-- plus une FK vers une table room, pour la même raison (voir current_room_id ci-dessus).
CREATE TABLE item (
    id           UUID PRIMARY KEY,
    template_id  UUID NOT NULL,
    room_id      UUID NULL,
    character_id UUID NULL REFERENCES character(id),
    slot         VARCHAR(20) NULL
);

-- DEFERRABLE INITIALLY DEFERRED : le slot-swap d'equipItem fait deux UPDATE dans la
-- même transaction (déséquiper l'ancien occupant, équiper le nouveau) ; une contrainte
-- non différée échouerait sur l'état transitoire entre les deux.
ALTER TABLE item
    ADD CONSTRAINT uniq_character_slot UNIQUE (character_id, slot) DEFERRABLE INITIALLY DEFERRED;

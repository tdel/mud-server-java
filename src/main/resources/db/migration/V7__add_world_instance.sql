-- world_template_id n'est pas une FK vers une table world_template : les WorldTemplate
-- sont chargés en mémoire depuis data/worlds/{monde}/world.json (voir
-- WorldTemplateService), pas persistés en DB. Validé côté application, même
-- convention que character.current_room_id (V1__init_schema.sql).
CREATE TABLE world_instance (
    id                       UUID PRIMARY KEY,
    world_template_id       UUID NOT NULL,
    party_leader_account_id UUID NULL REFERENCES account(id),
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table de jointure plutôt qu'une colonne tableau : composition figée à la création
-- d'une WorldInstance (une party qui lance un monde), cohérent avec le reste du
-- schéma relationnel.
CREATE TABLE world_instance_member (
    world_instance_id UUID NOT NULL REFERENCES world_instance(id),
    account_id         UUID NOT NULL REFERENCES account(id),
    PRIMARY KEY (world_instance_id, account_id)
);

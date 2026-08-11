-- Rattache chaque personnage à la WorldInstance qui l'héberge. L'id de la
-- WorldInstance par défaut ci-dessous (créée pour ne rien perdre des personnages
-- déjà existants) doit rester synchronisé avec
-- fr.idev.mudserver.domain.WorldInstance.DEFAULT_ID, et son world_template_id
-- avec l'id du monde "default" (voir data/worlds/default/world.json) :
-- littéraux fixes des deux côtés, pas générés à l'exécution, pour que ce
-- backfill reste déterministe et reproductible.
ALTER TABLE character ADD COLUMN world_instance_id UUID NULL REFERENCES world_instance(id);

INSERT INTO world_instance (id, world_template_id, party_leader_account_id, created_at)
VALUES ('a8e98a8e-73c1-43dd-b36e-a2f67f00ff48', 'f128833b-9a8a-4fb9-9796-33fd9413490d', NULL, now());

-- Cast explicite requis : dans un SELECT (contrairement à un INSERT ... VALUES),
-- Postgres n'infère pas le type UUID pour un littéral de chaîne à partir de la
-- colonne cible.
INSERT INTO world_instance_member (world_instance_id, account_id)
SELECT DISTINCT 'a8e98a8e-73c1-43dd-b36e-a2f67f00ff48'::uuid, account_id FROM character;

UPDATE character SET world_instance_id = 'a8e98a8e-73c1-43dd-b36e-a2f67f00ff48'::uuid WHERE world_instance_id IS NULL;

ALTER TABLE character ALTER COLUMN world_instance_id SET NOT NULL;

CREATE INDEX idx_character_world_instance_id ON character (world_instance_id);

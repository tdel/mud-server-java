-- Les potions deviennent stackables (piles de 100 au plus, cf. ItemType.maxStack) :
-- regroupe les exemplaires unitaires déjà en inventaire en piles par personnage et
-- par template. Les ids sont ceux des <consumable> de data/items/consumables.xml
-- (tous de type POTION) : les templates ne vivent pas en base.
-- Données uniquement, aucun changement de schéma : exclu du simulateur H2 de la
-- génération de code jOOQ, qui ne sait pas parser WITH RECURSIVE ... INSERT.
-- [jooq ignore start]
CREATE TEMP TABLE potion_total AS
SELECT character_id, template_id, SUM(quantity) AS total
FROM item
WHERE slot IS NULL
  AND character_id IS NOT NULL
  AND template_id IN ('019fa0a5-80bf-7e84-87bf-5cf699c00315', '6aeb7483-2694-4388-8c6b-f841fd5e48b4',
                      'f9083584-d5af-458c-bb9b-3ee7714452e3', '58373f5a-2f2a-4039-8a52-05ac333dd57f',
                      '3a4a5836-051e-4641-aef8-7b6e6792322e', '7c1e9a4b-2d6f-4e8a-9c3b-5f1a7d9b0c2e',
                      '8d2f0b5c-3e7a-4f9b-8d4c-6a2b8e0c1d3f', '9e3a1c6d-4f8b-4a0c-9e5d-7b3c9f1d2e4a')
GROUP BY character_id, template_id;

DELETE FROM item
WHERE slot IS NULL
  AND EXISTS (SELECT 1 FROM potion_total p
              WHERE p.character_id = item.character_id AND p.template_id = item.template_id);

-- Une ligne par pile de 100 (la dernière porte le reliquat), id = UUID v4 texte.
WITH RECURSIVE stack(character_id, template_id, remaining) AS (
    SELECT character_id, template_id, total FROM potion_total
    UNION ALL
    SELECT character_id, template_id, remaining - 100 FROM stack WHERE remaining > 100
)
INSERT INTO item (id, template_id, character_id, slot, enchant, quantity)
SELECT lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4'
           || substr(lower(hex(randomblob(2))), 2) || '-'
           || substr('89ab', 1 + (abs(random()) % 4), 1) || substr(lower(hex(randomblob(2))), 2) || '-'
           || lower(hex(randomblob(6))),
       template_id, character_id, NULL, 0, MIN(remaining, 100)
FROM stack;

DROP TABLE potion_total;
-- [jooq ignore stop]

-- Le mécanisme de repos court (short rest) n'a jamais été branché à une
-- commande en jeu : suppression pure, aucun rebuild de table nécessaire
-- pour un simple DROP COLUMN (voir CLAUDE.md, contrairement à un ajout de
-- contrainte).
ALTER TABLE character DROP COLUMN short_rest_count;

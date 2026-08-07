# Régénérer la démo

`demo.svg` est un enregistrement d'une vraie session telnet contre le serveur, produit avec
[termtosvg](https://github.com/nbedos/termtosvg). `record.py` pilote la session (frappe
simulée, timing réaliste) via une socket brute plutôt que le client `telnet` du système —
ce dernier fait sa propre négociation d'echo local, ce qui désynchronise l'envoi du mot de
passe quand il est piloté par un script plutôt que tapé par un humain.

## Prérequis

1. Le serveur doit tourner (port 4001) avec le Postgres de développement (`docker compose up
   -d db`, puis `mvn spring-boot:run` via Docker — voir le README à la racine).
2. Un personnage de démo doit déjà exister, **hors enregistrement** (le scénario de
   `record.py` va droit à `login`/`character-select` pour rester court) :
   ```
   register demo-account
   <mot de passe> DemoPassword123 (x2)
   character-create Aldric
   man
   human
   fighter
   ```
   Si les caractéristiques tirées donnent une Force trop basse (dégâts à mains nues nuls),
   `character-delete Aldric` puis recommencer `character-create` retire un nouveau jet.
3. Avant d'enregistrer, remettre Aldric dans un état propre : à la starting room, à PV
   pleins, avec assez d'or pour l'achat en boutique (le scénario achète une Épée courte et
   un Bouclier, 20 or au total), et sans objet résiduel d'une prise précédente (les achats
   sont persistés immédiatement en base, contrairement à la position — voir
   `CLAUDE.md`) :
   ```sql
   DELETE FROM item WHERE character_id = (SELECT id FROM character WHERE name = 'Aldric');
   UPDATE character SET current_room_id = '5e4ada37-37e1-438c-9233-581f10c055c7',
          current_health = max_health, gold = 100 WHERE name = 'Aldric';
   ```
   (`5e4ada37-...` est l'id de la starting room « Place du village » dans
   `data/rooms.json`.)
4. Redémarrer le serveur pour que le Bandit visé par la démo (room « Chemin du cimetière »)
   soit bien vivant et à pleine vie (les monstres sont en mémoire uniquement, jamais
   rechargés en cours de route).

## Outillage

`python3-venv` n'est pas installé sur toutes les machines et son installation demande
`sudo` ; à défaut, installation utilisateur directe :

```bash
pip3 install --user --break-system-packages -r docs/demo/requirements.txt
```

## Enregistrer

`termtosvg` a besoin d'un vrai pty attaché à son entrée/sortie standard pour fonctionner
(il bascule son propre terminal en mode raw) — un point qui casse silencieusement (démo
vide, aucune erreur) sous un shell non interactif. `script` (déjà présent sur la plupart des
systèmes Linux) en alloue un :

```bash
script -qec "$HOME/.local/bin/termtosvg docs/demo/demo.svg -c 'python3 docs/demo/record.py' -g 100x30 -m 100 -M 4000" /dev/null
```

- `-g 100x30` : géométrie du terminal (100 colonnes suffisent pour les lignes les plus
  longues du jeu — stats, description de room).
- `-m`/`-M` : durée mini/maxi d'une frame. `-M` doit rester au-dessus de `BEAT_PAUSE` dans
  `record.py` (actuellement 3.5s) sous peine de tronquer les pauses volontairement longues
  que le script marque après chaque étape clé — c'est ce qui donne son rythme posé à la
  démo, pas juste `-M` seul.

Vérifier ensuite que `demo.svg` contient bien le texte attendu (pas un enregistrement vide),
y compris les étapes ajoutées par le scénario actuel (grille hexagonale, boutique, butin, et
la room explorée après le combat). `termtosvg` encode les caractères accentués en entités
XML (`è` devient `&#232;`), donc grep sur un nom de room accentué doit se limiter à la
sous-chaîne sans accent :

```bash
grep -c "Bandit" docs/demo/demo.svg
grep -c "Aubergiste" docs/demo/demo.svg
grep -c "Cimeti" docs/demo/demo.svg  # matche "Chemin du cimetière" ET "Cimetière abandonné"
```

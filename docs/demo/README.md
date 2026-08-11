# Régénérer la démo

`demo.svg` est un enregistrement de deux vraies sessions telnet contre le serveur, jouées
côte à côte dans un split-screen [tmux](https://github.com/tmux/tmux) et capturées avec
[termtosvg](https://github.com/nbedos/termtosvg). Chaque panneau tmux fait tourner son
propre script Python (`record_player1.py` pour Aldric, `record_player2.py` pour Elowen), qui
pilote sa session via une socket brute plutôt que le client `telnet` du système — ce dernier
fait sa propre négociation d'echo local, ce qui désynchronise l'envoi du mot de passe quand
il est piloté par un script plutôt que tapé par un humain. Les deux scripts partagent la même
logique socket/frappe via `telnet_client.py`.

Les deux scripts sont des process indépendants, sans communication entre eux : toute la
mise en scène (qui parle quand, qui arrive au combat après qui) repose sur des `time.sleep`
calés à la main, pas sur une vraie synchronisation — voir les commentaires en tête de
`record_player2.py`. Si le moment clé (Elowen qui rejoint le combat d'Aldric) ne "prend" pas
sur une prise, la manière la plus simple de corriger le tir est de relancer tout
l'enregistrement plutôt que de complexifier la synchronisation.

## Prérequis

1. Le serveur doit tourner (port 4001) avec le Postgres de développement (`docker compose up
   -d db`, puis `mvn spring-boot:run` via Docker — voir le README à la racine).
2. **Deux** personnages de démo doivent déjà exister, **hors enregistrement** (les scénarios
   vont droit à `world-enter`/`character-select` pour rester courts) :
   ```
   register demo-account
   <mot de passe> DemoPassword123 (x2)
   world-enter default
   character-create Aldric
   man
   human
   fighter
   ```
   ```
   register demo-account-2
   <mot de passe> DemoPassword123 (x2)
   world-enter default
   character-create Elowen
   woman
   elf
   cleric
   ```
   Les deux comptes doivent atterrir dans la **même** WorldInstance (`world-enter default`,
   pas de Party — voir la note dans `record_player1.py`/`record_player2.py` sur pourquoi le
   chemin Party n'est pas utilisé ici : il matérialiserait une instance neuve à chaque run,
   ce qui forcerait un `character-create` en direct avec un jet de caractéristiques aléatoire
   à chaque prise). Si les caractéristiques tirées donnent une Force trop basse (dégâts à
   mains nues nuls) pour Aldric, `character-delete Aldric` puis recommencer
   `character-create` retire un nouveau jet — idem pour Elowen si besoin.
3. Avant d'enregistrer, remettre les deux personnages dans un état propre : à la starting
   room, à PV pleins, avec assez d'or pour l'achat en boutique (le scénario d'Aldric achète
   une Épée courte et un Bouclier, 20 or au total), et sans objet résiduel d'une prise
   précédente (les achats sont persistés immédiatement en base, contrairement à la position
   — voir `CLAUDE.md`) :
   ```sql
   DELETE FROM item WHERE character_id IN (SELECT id FROM character WHERE name IN ('Aldric', 'Elowen'));
   UPDATE character SET current_room_id = '5e4ada37-37e1-438c-9233-581f10c055c7',
          current_health = max_health, gold = 100 WHERE name IN ('Aldric', 'Elowen');
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

`tmux` doit également être installé (`apt install tmux`, nécessite `sudo` sur une machine où
il serait absent).

## Enregistrer

`termtosvg` a besoin d'un vrai pty attaché à son entrée/sortie standard pour fonctionner
(il bascule son propre terminal en mode raw) — un point qui casse silencieusement (démo
vide, aucune erreur) sous un shell non interactif. `script` (déjà présent sur la plupart des
systèmes Linux) en alloue un ; tmux gère lui-même les deux panneaux à l'intérieur de ce
pty unique, donc pas besoin d'un second niveau d'allocation :

```bash
script -qec "$HOME/.local/bin/termtosvg docs/demo/demo.svg -c 'docs/demo/record_two_players.sh' -g 201x30 -m 100 -M 4000" /dev/null
```

- `-g 201x30` : géométrie du terminal — deux panneaux de 100 colonnes (largeur qui suffisait
  déjà à la démo à un seul joueur pour les lignes les plus longues du jeu — stats, description
  de room) plus 1 colonne de bordure tmux entre les deux. À vérifier à l'œil pendant
  l'enregistrement : si la grille hexagonale ou le tableau `stats` wrap mal dans un panneau,
  élargir (`-g 211x30` pour 105 colonnes/panneau, ou augmenter `-y` si une description de
  room est tronquée verticalement).
- `-m`/`-M` : durée mini/maxi d'une frame. `-M` doit rester au-dessus de `BEAT_PAUSE` dans
  `telnet_client.py` (actuellement 3.5s) sous peine de tronquer les pauses volontairement
  longues que les scripts marquent après chaque étape clé — c'est ce qui donne son rythme
  posé à la démo, pas juste `-M` seul.

Vérifier ensuite que `demo.svg` contient bien le texte attendu (pas un enregistrement vide),
y compris les étapes des deux personnages (grille hexagonale, boutique, butin, la room
explorée après le combat, et l'interaction entre Aldric et Elowen). `termtosvg` encode les
caractères accentués en entités XML (`è` devient `&#232;`), donc grep sur un nom de room
accentué doit se limiter à la sous-chaîne sans accent :

```bash
grep -c "Bandit" docs/demo/demo.svg
grep -c "Aubergiste" docs/demo/demo.svg
grep -c "Cimeti" docs/demo/demo.svg    # matche "Chemin du cimetière" ET "Cimetière abandonné"
grep -c "Elowen" docs/demo/demo.svg
grep -c "joins the fight" docs/demo/demo.svg  # Elowen a bien rejoint le combat d'Aldric
grep -c "says:" docs/demo/demo.svg            # l'échange "say" entre les deux joueurs
```

#!/usr/bin/env python3
"""Panneau tmux droit de la démo à deux joueurs (voir docs/demo/README.md et
docs/demo/record_two_players.sh) : Elowen (compte "demo-account-2"), qui
rejoint Aldric (record_player1.py) dans la même WorldInstance par défaut,
échange quelques répliques `say` avec lui, puis rejoint le combat contre le
Bandit déjà engagé par Aldric ("attack bandit" sur un combat en cours produit
"You join the fight!" / "<name> joins the fight!", visible sur les deux
panneaux).

Le personnage "Elowen" (femme, elfe, clerc) doit déjà exister (register +
character-create, hors enregistrement) — voir docs/demo/README.md.

PLAYER2_START_DELAY retarde la connexion pour qu'Elowen n'interfère pas avec
la scène boutique d'Aldric et arrive au Chemin du cimetière juste après que
le Bandit ait repéré Aldric — à recaler à l'oreille si le rythme des deux
scripts change (voir docs/demo/README.md, aucune coordination inter-process
n'existe, tout est en `time.sleep`).
"""

import time

from telnet_client import BEAT_PAUSE, DEFAULT_PAUSE, connect, send_command

PLAYER2_START_DELAY = 36  # secondes avant même l'ouverture de la connexion


def main():
    time.sleep(PLAYER2_START_DELAY)

    sock = connect()

    send_command(sock, "login demo-account-2")
    send_command(sock, "DemoPassword123", echo=False)  # mot de passe : jamais affiché

    # Même instance par défaut qu'Aldric (pas de Party — voir docs/demo/README.md pour
    # pourquoi le chemin party n'est pas utilisé ici).
    send_command(sock, "world-enter default", post_pause=BEAT_PAUSE)
    send_command(sock, "character-select", post_pause=BEAT_PAUSE)  # argument ignoré

    send_command(sock, "look", post_pause=BEAT_PAUSE)
    send_command(sock, "stats", post_pause=BEAT_PAUSE)

    # "say" est diffusé à toute la WorldInstance : Elowen répond au message d'Aldric même
    # si elle n'est pas encore dans sa room.
    send_command(sock, "say J'arrive, garde-le en vie !", post_pause=BEAT_PAUSE)

    # Rejoint Aldric au Chemin du cimetière en réutilisant SON trajet déjà validé à la
    # main plutôt que d'en improviser un second (voir l'avertissement dans
    # record_player1.py sur la fragilité des traversées de portails hexagonaux).
    outcome = join_and_fight(sock)

    if outcome is True:
        send_command(sock, "say Bien joue !", post_pause=BEAT_PAUSE)
    elif outcome is None:
        # Le Bandit était déjà à terre (ou introuvable) à l'arrivée d'Elowen : rien à
        # rejoindre, on ne force pas de nouvelle tentative depuis ce panneau.
        send_command(sock, "look", post_pause=BEAT_PAUSE)

    send_command(sock, "inventory", post_pause=BEAT_PAUSE)

    time.sleep(2.0)
    send_command(sock, "logout", post_pause=1.0)
    sock.close()


def join_and_fight(sock):
    """Marche jusqu'au Chemin du cimetière (même trajet que record_player1.py) puis tente
    de rejoindre le combat contre le Bandit déjà engagé par Aldric. Le Bandit peut aussi
    repérer Elowen directement pendant la marche (sa zone d'aggro n'est pas exclusive à
    Aldric), donc chaque pas est vérifié comme dans record_player1.py plutôt que de
    présumer qu'"attack bandit" sera toujours le déclencheur. Retourne True si le Bandit
    est vaincu, False si Elowen est vaincue, None si le combat était déjà terminé (ou le
    Bandit introuvable) à son arrivée — aucun de ces trois cas ne coordonne avec le retry
    indépendant de record_player1.py (voir docs/demo/README.md)."""
    steps = [
        ("go sw 6", DEFAULT_PAUSE),
        ("go w 5", DEFAULT_PAUSE),
        ("go w 6", DEFAULT_PAUSE),
        ("go w 1", BEAT_PAUSE),
        ("go sw 6", DEFAULT_PAUSE),
        ("go w 4", BEAT_PAUSE),
    ]
    already_fighting = False
    for command, pause in steps:
        text = send_command(sock, command, post_pause=pause)
        if "notices you" in text or "You join the fight" in text:
            already_fighting = True
            break

    if not already_fighting:
        text = send_command(sock, "attack bandit", post_pause=2.4)
        joined = (
            "You join the fight" in text
            or "attacks the Bandit" in text
            or "collapses, defeated" in text
            or "You collapse, defeated" in text
        )
        if not joined:
            return None
        if "collapses, defeated" in text or "fight is over" in text.lower():
            return True
        if "You collapse, defeated" in text:
            return False

    for _ in range(8):
        text = send_command(sock, "attack bandit", post_pause=2.4)
        if "collapses, defeated" in text or "fight is over" in text.lower():
            return True
        if "You collapse, defeated" in text:
            return False
    return False


if __name__ == "__main__":
    main()

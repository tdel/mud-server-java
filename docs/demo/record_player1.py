#!/usr/bin/env python3
"""Panneau tmux gauche de la démo à deux joueurs (voir docs/demo/README.md et
docs/demo/record_two_players.sh) : Aldric (compte "demo-account"), le
scénario "historique" (boutique, équipement) puis le combat contre le
Bandit, avec deux répliques `say` échangées avec Elowen (record_player2.py)
avant/après le combat.

Le personnage "Aldric" doit déjà exister (register + character-create, hors
enregistrement) — voir docs/demo/README.md pour le détail et pour le reset
SQL à faire avant chaque prise.

La case d'apparition d'un personnage dans une room n'est pas parfaitement
déterministe (elle dépend de l'ordre d'attribution des cases, y compris avec
un seul personnage présent) : traverser un portail nécessite d'atteindre une
case précise, donc les trajets qui en traversent un utilisent
`telnet_client.walk_until_room` (avance case par case jusqu'à changer
effectivement de room) plutôt qu'un nombre de pas fixe — contrairement à
l'approche du Bandit (`approach_and_fight` ci-dessous), où n'importe quelle
case dans sa zone d'aggro suffit et un trajet à pas fixes reste fiable.
record_player2.py réutilise ce même trajet vers le Bandit plutôt que d'en
improviser un second.
"""

import time

from telnet_client import (
    BEAT_PAUSE,
    DEFAULT_PAUSE,
    connect,
    send_command,
    walk_until_room,
)


def main():
    sock = connect()

    send_command(sock, "login demo-account")
    send_command(sock, "DemoPassword123", echo=False)  # mot de passe : jamais affiché

    # Depuis le multi-world (Lobby/WorldInstance), il faut désormais choisir un monde
    # avant de pouvoir sélectionner un personnage — Aldric vit déjà dans l'instance par
    # défaut, "world-enter" y ramène directement (chemin solo, pas de Party).
    send_command(sock, "world-enter default", post_pause=BEAT_PAUSE)
    send_command(sock, "character-select", post_pause=BEAT_PAUSE)  # argument ignoré

    # "look" affiche la grille hexagonale de la room courante (cases, portails, occupants).
    send_command(sock, "look", post_pause=BEAT_PAUSE)

    # Fiche de personnage et jet de compétence maîtrisée (classes/compétences par classe).
    send_command(sock, "stats", post_pause=BEAT_PAUSE)
    send_command(sock, "check perception 12", post_pause=BEAT_PAUSE)

    # Déplacement sur la grille hexagonale, de la Place du village vers la Taverne (à
    # l'est). La case d'apparition d'Aldric n'est pas parfaitement déterministe (elle
    # varie selon les runs, y compris sans Elowen dans la room), donc un compte de pas
    # fixe comme l'ancien "go e 6" + "go e 1" à un seul joueur n'est plus fiable : on
    # avance case par case jusqu'à effectivement changer de room (voir
    # telnet_client.walk_until_room et docs/demo/README.md).
    walk_until_room(sock, "e", "Taverne")
    time.sleep(BEAT_PAUSE - DEFAULT_PAUSE)

    # PNJ marchand : dialogue, catalogue, achat d'une épée et d'un bouclier.
    send_command(sock, "talk aubergiste", post_pause=BEAT_PAUSE)
    send_command(sock, "1", post_pause=BEAT_PAUSE)  # "Voir ce que tu vends"
    send_command(sock, "Epée courte", post_pause=BEAT_PAUSE)
    send_command(sock, "Bouclier", post_pause=BEAT_PAUSE)
    send_command(sock, "0")  # retour au menu de dialogue
    send_command(sock, "3", post_pause=BEAT_PAUSE)  # "Au revoir"

    # Équiper les achats avant le combat.
    send_command(sock, "equip Epée courte", post_pause=1.6)
    send_command(sock, "equip Bouclier", post_pause=BEAT_PAUSE)

    # Retour vers la Place du village : on s'écarte du portail (bord de la Taverne) puis on
    # y revient pour le retraverser en sens inverse (voir la note en tête de fichier).
    send_command(sock, "go e 1")
    walk_until_room(sock, "w", "Place du village")
    time.sleep(BEAT_PAUSE - DEFAULT_PAUSE)

    # "say" est diffusé à toute la WorldInstance (pas juste la room courante) : Elowen le
    # voit dès qu'elle est en jeu, même avant d'avoir rejoint la room du Bandit.
    send_command(sock, "say Elowen, tu es prete pour le Bandit ?", post_pause=BEAT_PAUSE)

    # Le combat contre le Bandit (11 PV) reste soumis aux dés : une défaite renvoie Aldric à
    # la Place du village, PV pleins, pendant que le Bandit reste sur place (déjà entamé).
    # On retente donc jusqu'à victoire plutôt que de figer un résultat par des stats
    # gonflées artificiellement — quelques essais suffisent toujours à l'achever. Ce retry
    # est propre à ce process : aucune coordination avec record_player2.py, qui gère sa
    # propre arrivée et son propre retry indépendamment (voir docs/demo/README.md).
    fight_won = False
    for _ in range(3):
        if approach_and_fight(sock):
            fight_won = True
            break

    if fight_won:
        send_command(sock, "say Bien joue, il est tombe !", post_pause=BEAT_PAUSE)
        send_command(sock, "look", post_pause=BEAT_PAUSE)

        # Une room de plus au-delà du Chemin du cimetière, une fois le Bandit vaincu.
        send_command(sock, "go sw 6")
        walk_until_room(sock, "w", "abandonn")  # "Cimetière abandonné"
        time.sleep(BEAT_PAUSE - DEFAULT_PAUSE)
        send_command(sock, "look", post_pause=BEAT_PAUSE)

    send_command(sock, "inventory", post_pause=BEAT_PAUSE)

    time.sleep(2.0)
    send_command(sock, "logout", post_pause=1.0)
    sock.close()


def approach_and_fight(sock):
    """De la Place du village au Chemin du cimetière, puis combat. L'approche du Bandit
    déclenche le combat automatiquement via sa zone de présence, sans commande "attack" —
    c'est le mouvement lui-même qui l'amorce. Le point exact où l'aggro se déclenche varie
    d'une tentative à l'autre (case de réapparition après une défaite, présence d'Elowen
    dans la room de départ), donc chaque pas du trajet est vérifié plutôt que seulement le
    dernier — dès que l'aggro se déclenche, le reste du trajet est inutile (le serveur
    bloque déjà tout déplacement pendant un combat) et attaquer immédiatement évite de
    rester bloqué en combat sans jamais agir. Retourne True si le Bandit est vaincu, False
    en cas de défaite d'Aldric (renvoyé à la Place du village par le serveur) ou d'échec à
    déclencher le combat."""
    steps = [
        ("go sw 6", DEFAULT_PAUSE),
        ("go w 5", DEFAULT_PAUSE),
        ("go w 6", DEFAULT_PAUSE),
        ("go w 1", BEAT_PAUSE),
        ("go sw 6", DEFAULT_PAUSE),
        ("go w 4", BEAT_PAUSE),
    ]
    combat_started = False
    for command, pause in steps:
        text = send_command(sock, command, post_pause=pause)
        if "notices you" in text or "You join the fight" in text:
            combat_started = True
            break

    if not combat_started:
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

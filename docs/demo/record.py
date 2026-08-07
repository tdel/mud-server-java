#!/usr/bin/env python3
"""Pilote une vraie session telnet contre le serveur MUD (localhost:4001) pour produire
l'enregistrement termtosvg de docs/demo/demo.svg — voir docs/demo/README.md pour les
prérequis et la commande d'enregistrement complète.

Connexion socket brute plutôt que le client `telnet` du système : ce dernier fait sa propre
négociation IAC/echo local, ce qui désynchronise l'envoi du mot de passe quand il est piloté
par un script plutôt que tapé par un humain. Ici, la frappe est simulée nous-mêmes
(caractère par caractère, avec un vrai délai) et affichée directement sur la sortie standard
— c'est cette sortie que `termtosvg` enregistre.

Le personnage "Aldric" (compte "demo-account") doit déjà exister : login/mot de passe/
character-select ci-dessous supposent qu'il a été créé une fois via le flux normal
(register + character-create), hors enregistrement, pour garder la démo courte.

Le trajet à pied (série de "go <direction> <n>") a été validé à la main contre le serveur
local : franchir un portail vous dépose exactement sur la case-portail de la room
d'arrivée (bord de la grille), d'où il est impossible de continuer dans la même direction
(bloqué par les limites) — il faut s'en écarter puis y revenir pour retraverser en sens
inverse. Ne pas modifier ce trajet sans revalider à la main (docs/demo/README.md).
"""

import socket
import sys
import time

HOST = "localhost"
PORT = 4001
TYPE_DELAY = 0.07  # secondes entre deux caractères tapés (simulation de frappe)
DEFAULT_PAUSE = 2.2  # secondes de battement après une commande, pour laisser le temps de lire
BEAT_PAUSE = 3.5  # pause plus longue après un moment clé de la démo


def strip_telnet_iac(data: bytes) -> bytes:
    """Retire les séquences IAC (négociation d'option, ex. le toggle d'echo du mot de
    passe côté serveur — TelnetEcho.OFF/ON) : un vrai client telnet les interprète sans
    jamais les afficher, un décodage UTF-8 brut les transformerait en "�" visibles.
    """
    IAC = 0xFF
    cleaned = bytearray()
    i = 0
    while i < len(data):
        b = data[i]
        if b != IAC:
            cleaned.append(b)
            i += 1
            continue
        if i + 1 >= len(data):
            break
        cmd = data[i + 1]
        if cmd == IAC:  # IAC IAC échappé : un vrai 0xFF dans le flux
            cleaned.append(IAC)
            i += 2
            continue
        if 251 <= cmd <= 254:  # WILL/WONT/DO/DONT <option>
            i += 3
            continue
        i += 2  # autre commande IAC à 2 octets
    return bytes(cleaned)


def out(text):
    sys.stdout.write(text)
    sys.stdout.flush()


def type_line(line, echo=True):
    for ch in line if echo else "":
        out(ch)
        time.sleep(TYPE_DELAY)
    if not echo:
        time.sleep(TYPE_DELAY * len(line))  # même durée de "frappe", rien à l'écran
    out("\n")


def recv_all(sock, wait=0.3, settle=0.6):
    """Lit tout ce qui est disponible, avec une petite accalmie pour laisser passer les
    réponses en plusieurs morceaux (ex. un tour de combat qui déclenche plusieurs messages).
    """
    time.sleep(wait)
    sock.settimeout(settle)
    buf = b""
    try:
        while True:
            chunk = sock.recv(65536)
            if not chunk:
                break
            buf += chunk
    except socket.timeout:
        pass
    return strip_telnet_iac(buf).decode(errors="replace")


def send_command(sock, line, post_pause=DEFAULT_PAUSE, echo=True):
    type_line(line, echo=echo)
    sock.sendall((line + "\n").encode())
    text = recv_all(sock)
    out(text)
    time.sleep(post_pause)
    return text


def main():
    sock = socket.create_connection((HOST, PORT), timeout=5)
    out(recv_all(sock, wait=0.2, settle=0.5))
    time.sleep(1.5)

    send_command(sock, "login demo-account")
    send_command(sock, "DemoPassword123", echo=False)  # mot de passe : jamais affiché
    send_command(sock, "character-select Aldric", post_pause=BEAT_PAUSE)

    # "look" affiche la grille hexagonale de la room courante (cases, portails, occupants).
    send_command(sock, "look", post_pause=BEAT_PAUSE)

    # Fiche de personnage et jet de compétence maîtrisée (classes/compétences par classe).
    send_command(sock, "stats", post_pause=BEAT_PAUSE)
    send_command(sock, "check perception 12", post_pause=BEAT_PAUSE)

    # Déplacement sur la grille hexagonale, de la Place du village vers la Taverne (à l'est).
    send_command(sock, "go e 6")
    send_command(sock, "go e 1", post_pause=BEAT_PAUSE)

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
    send_command(sock, "go w 1", post_pause=BEAT_PAUSE)

    # Le combat contre le Bandit (11 PV) reste soumis aux dés : une défaite renvoie Aldric à
    # la Place du village, PV pleins, pendant que le Bandit reste sur place (déjà entamé).
    # On retente donc jusqu'à victoire plutôt que de figer un résultat par des stats
    # gonflées artificiellement — quelques essais suffisent toujours à l'achever.
    fight_won = False
    for _ in range(3):
        if approach_and_fight(sock):
            fight_won = True
            break

    if fight_won:
        time.sleep(BEAT_PAUSE)
        send_command(sock, "look", post_pause=BEAT_PAUSE)

        # Une room de plus au-delà du Chemin du cimetière, une fois le Bandit vaincu : même
        # logique de portail (s'écarter puis y revenir) que pour les traversées précédentes.
        send_command(sock, "go sw 6")
        send_command(sock, "go w 5", post_pause=BEAT_PAUSE)
        send_command(sock, "look", post_pause=BEAT_PAUSE)

    send_command(sock, "inventory", post_pause=BEAT_PAUSE)

    time.sleep(2.0)
    send_command(sock, "logout", post_pause=1.0)
    sock.close()


def approach_and_fight(sock):
    """De la Place du village au Chemin du cimetière, puis combat. L'approche du Bandit
    déclenche le combat automatiquement via sa zone de présence, sans commande "attack" —
    c'est le mouvement lui-même qui l'amorce. Retourne True si le Bandit est vaincu, False
    en cas de défaite d'Aldric (renvoyé à la Place du village par le serveur) ou d'échec à
    déclencher le combat."""
    send_command(sock, "go sw 6")
    send_command(sock, "go w 5")
    send_command(sock, "go w 6")
    send_command(sock, "go w 1", post_pause=BEAT_PAUSE)

    send_command(sock, "go sw 6")
    text = send_command(sock, "go w 4", post_pause=BEAT_PAUSE)

    if "notices you" not in text and "join the fight" not in text:
        return False

    for _ in range(8):
        text = send_command(sock, "attack bandit", post_pause=2.4)
        if "collapses" in text or "fight is over" in text.lower():
            return True
        if "collapse, defeated" in text:
            return False
    return False


if __name__ == "__main__":
    main()

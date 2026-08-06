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
"""

import socket
import sys
import time

HOST = "localhost"
PORT = 4001
TYPE_DELAY = 0.05  # secondes entre deux caractères tapés (simulation de frappe)


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


def send_command(sock, line, post_pause=0.6, echo=True):
    type_line(line, echo=echo)
    sock.sendall((line + "\n").encode())
    text = recv_all(sock)
    out(text)
    time.sleep(post_pause)
    return text


def main():
    sock = socket.create_connection((HOST, PORT), timeout=5)
    out(recv_all(sock, wait=0.2, settle=0.5))
    time.sleep(1.2)

    send_command(sock, "login demo-account")
    send_command(sock, "DemoPassword123", echo=False)  # mot de passe : jamais affiché
    send_command(sock, "character-select Aldric", post_pause=1.0)
    send_command(sock, "go sud", post_pause=1.0)

    for _ in range(8):
        text = send_command(sock, "attack bandit")
        if "collapses" in text or "fight is over" in text.lower():
            break

    time.sleep(1.2)
    send_command(sock, "logout", post_pause=1.0)
    sock.close()


if __name__ == "__main__":
    main()

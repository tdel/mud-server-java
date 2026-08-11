"""Aide partagée pour piloter une vraie session telnet contre le serveur MUD
(localhost:4001) — utilisée par `record_player1.py` et `record_player2.py`
pour produire l'enregistrement termtosvg à deux joueurs de
`docs/demo/demo.svg` (voir `docs/demo/README.md`).

Connexion socket brute plutôt que le client `telnet` du système : ce dernier
fait sa propre négociation IAC/echo local, ce qui désynchronise l'envoi du
mot de passe quand il est piloté par un script plutôt que tapé par un humain.
Ici, la frappe est simulée nous-mêmes (caractère par caractère, avec un vrai
délai) et affichée directement sur la sortie standard — c'est cette sortie
que `termtosvg` enregistre (une instance de ce module par panneau tmux).
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


def room_name(text):
    """Extrait la ligne "== Nom de la room ==" d'une réponse de commande."""
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("=="):
            return line
    return ""


def walk_until_room(sock, direction, target_substring, max_steps=10, pause=DEFAULT_PAUSE):
    """Avance case par case dans `direction` (un "go <direction> 1" par pas) jusqu'à
    atteindre une room dont le nom contient `target_substring`, ou jusqu'à épuiser
    `max_steps`. La case d'apparition/réapparition d'un personnage n'est pas parfaitement
    déterministe (elle dépend des autres personnages déjà présents dans la room et de
    l'ordre d'attribution des cases), donc un nombre de pas fixe n'est pas fiable pour
    traverser un portail (qui exige d'atteindre une case précise) — contrairement à une
    approche de monstre, où n'importe quelle case dans sa zone d'aggro suffit. Retourne le
    texte de la dernière commande envoyée.
    """
    text = ""
    for _ in range(max_steps):
        text = send_command(sock, f"go {direction} 1", post_pause=pause)
        if target_substring in room_name(text):
            break
    return text


def connect(host=HOST, port=PORT, initial_wait=0.2, initial_settle=0.5, post_connect_sleep=1.5):
    """Ouvre la connexion, affiche le banner de bienvenue et laisse un temps de lecture
    avant la première commande — factorisé ici plutôt que dupliqué en tête de chaque
    script joueur.
    """
    sock = socket.create_connection((host, port), timeout=5)
    out(recv_all(sock, wait=initial_wait, settle=initial_settle))
    time.sleep(post_connect_sleep)
    return sock

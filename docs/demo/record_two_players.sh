#!/usr/bin/env bash
# Orchestration tmux de la démo à deux joueurs : ouvre une session détachée avec deux
# panneaux côte à côte (chacun sa propre session telnet/socket via record_player1.py /
# record_player2.py), s'attache, et se termine proprement quand le scénario d'Aldric (le
# plus long des deux) rend la main. C'est CE script, pas un script joueur, qui devient
# l'argument -c de termtosvg — voir docs/demo/README.md pour la commande complète.
set -euo pipefail

SESSION="mud-demo"
DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

tmux kill-session -t "$SESSION" 2>/dev/null || true

tmux new-session -d -s "$SESSION" -x 201 -y 30 -n demo
tmux set-option -t "$SESSION" pane-border-status top
tmux set-option -t "$SESSION" pane-border-format "#{pane_title}"

tmux split-window -h -t "$SESSION:demo"
tmux select-pane -t "$SESSION:demo.0" -T "Joueur 1 — Aldric"
tmux select-pane -t "$SESSION:demo.1" -T "Joueur 2 — Elowen"

tmux send-keys -t "$SESSION:demo.1" "python3 '$DEMO_DIR/record_player2.py'" Enter
tmux send-keys -t "$SESSION:demo.0" "python3 '$DEMO_DIR/record_player1.py'; tmux kill-session -t $SESSION" Enter

tmux attach-session -t "$SESSION"

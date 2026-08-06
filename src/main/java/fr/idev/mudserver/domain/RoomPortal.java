package fr.idev.mudserver.domain;

/**
 * Remplace {@link RoomExit} (supprimé) : un portail entre deux Rooms n'est plus
 * indexé par une direction textuelle libre (au plus un exit par direction),
 * mais par une case précise sur le bord de la grille source — une Room peut
 * avoir plusieurs portails "vaguement à l'est" menant à des Rooms différentes,
 * ce que l'ancien modèle ne permettait pas d'exprimer. {@code direction}
 * redevient une simple métadonnée d'affichage (résumé de portails dans
 * {@code Look}) : la recherche se fait via
 * {@link Room#findPortalAt(HexCoordinate)}, jamais par cette étiquette.
 * Volontairement pas de validation géométrique de {@code direction} contre le
 * bord réel de {@code cell} (ambigu dans les coins) — traité comme un choix
 * d'autorat, pas une contrainte.
 */
public record RoomPortal(HexCoordinate cell, String direction, Room sourceRoom, Room targetRoom,
        HexCoordinate targetCell) {
}

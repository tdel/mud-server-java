package fr.idev.mudserver.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Coordonnée axiale (q, r) d'une case hexagonale pointy-top. La grille d'une
 * {@link Room} n'utilise pas de coordonnées offset (rangée/colonne) séparées de
 * la représentation de stockage : {@code q}/{@code r} bornés par
 * {@code width}/{@code height} servent à la fois de clé de stockage et de base
 * aux calculs de voisinage/distance, au prix d'une grille en parallélogramme
 * plutôt qu'un rectangle offset parfait — invisible en rendu texte telnet
 * monospace.
 */
public record HexCoordinate(int q, int r) {

    public HexCoordinate neighbor(HexDirection direction) {
        return new HexCoordinate(q + direction.dq(), r + direction.dr());
    }

    /**
     * Distance axiale standard entre deux cases (nombre minimal de pas pour aller
     * de l'une à l'autre).
     */
    public int distanceTo(HexCoordinate other) {
        int dq = q - other.q;
        int dr = r - other.r;
        return (Math.abs(dq) + Math.abs(dq + dr) + Math.abs(dr)) / 2;
    }

    /**
     * Énumère toutes les cases à distance {@code <= radius} de {@code this},
     * {@code this} inclus. Non utilisé par le déplacement/l'affichage en dehors du
     * viewport de {@code Look} dans cette phase — c'est le point d'accroche pour
     * une future portée d'attaque/de ciblage.
     */
    public List<HexCoordinate> withinRadius(int radius) {
        List<HexCoordinate> cells = new ArrayList<>();
        for (int dq = -radius; dq <= radius; dq++) {
            int rMin = Math.max(-radius, -dq - radius);
            int rMax = Math.min(radius, -dq + radius);
            for (int dr = rMin; dr <= rMax; dr++) {
                cells.add(new HexCoordinate(q + dq, r + dr));
            }
        }
        return cells;
    }
}

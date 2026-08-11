package fr.idev.mudserver.domain;

import java.util.Locale;
import java.util.Optional;

public enum HexDirection {

    E(1, 0), NE(1, -1), NW(0, -1), W(-1, 0), SW(-1, 1), SE(0, 1);

    private final int dq;
    private final int dr;

    HexDirection(int dq, int dr) {
        this.dq = dq;
        this.dr = dr;
    }

    public int dq() {
        return dq;
    }

    public int dr() {
        return dr;
    }

    public HexDirection opposite() {
        return switch (this) {
            case E -> W;
            case W -> E;
            case NE -> SW;
            case SW -> NE;
            case NW -> SE;
            case SE -> NW;
        };
    }

    public static Optional<HexDirection> fromToken(String token) {
        String normalized = token.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "e", "est" -> Optional.of(E);
            case "w", "ouest" -> Optional.of(W);
            case "ne", "nord-est" -> Optional.of(NE);
            case "nw", "nord-ouest" -> Optional.of(NW);
            case "se", "sud-est" -> Optional.of(SE);
            case "sw", "sud-ouest" -> Optional.of(SW);
            default -> Optional.empty();
        };
    }
}

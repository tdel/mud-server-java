package app.domain.actor;

// Attributs de Lineage 2, sur l'échelle retail (Human Fighter : STR 40, DEX 30,
// CON 43, INT 21, WIT 11, MEN 25 ; monstres : 40/30/43/21/20/20).
public enum Attribute {
    STR, DEX, CON, INT, WIT, MEN;

    public String label() {
        return switch (this) {
            case STR -> "Strength";
            case DEX -> "Dexterity";
            case CON -> "Constitution";
            case INT -> "Intelligence";
            case WIT -> "Wit";
            case MEN -> "Mental";
        };
    }

    // Multiplicateur L2 de l'attribut (data/stats/statBonus.xml du datapack L2J
    // High Five, qui tabule ces formules arrondies au centième) : STR -> p.atk,
    // INT -> m.atk, CON -> PV/p.def/régén, MEN -> PM/m.def/résistance, DEX ->
    // précision/esquive/critique/atk.spd, WIT -> cast.spd/critique magique.
    public double bonus(int score) {
        double raw = switch (this) {
            case STR -> Math.pow(1.036, score - 34.845);
            case INT -> Math.pow(1.020, score - 31.375);
            case CON -> Math.pow(1.030, score - 27.632);
            case MEN -> Math.pow(1.010, score + 0.060);
            case DEX -> Math.pow(1.009, score - 19.360);
            case WIT -> Math.pow(1.050, score - 20.000);
        };
        return Math.round(raw * 100) / 100.0;
    }
}

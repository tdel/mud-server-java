package app.domain.actor;

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
}

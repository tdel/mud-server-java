package app.domain.actor;

public enum Attribute {
    STRENGTH, DEXTERITY, CONSTITUTION, INTELLIGENCE, WIT, MEN;

    public String label() {
        return switch (this) {
            case STRENGTH -> "Strength";
            case DEXTERITY -> "Dexterity";
            case CONSTITUTION -> "Constitution";
            case INTELLIGENCE -> "Intelligence";
            case WIT -> "Wit";
            case MEN -> "Mental";
        };
    }
}

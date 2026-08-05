package fr.idev.mudserver.domain.actor;

public enum Attribute {
    STRENGTH, DEXTERITY, CONSTITUTION, INTELLIGENCE, WISDOM, CHARISMA;

    public String label() {
        return switch (this) {
            case STRENGTH -> "Strength";
            case DEXTERITY -> "Dexterity";
            case CONSTITUTION -> "Constitution";
            case INTELLIGENCE -> "Intelligence";
            case WISDOM -> "Wisdom";
            case CHARISMA -> "Charisma";
        };
    }
}

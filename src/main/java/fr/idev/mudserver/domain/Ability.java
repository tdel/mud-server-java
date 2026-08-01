package fr.idev.mudserver.domain;

public enum Ability {
    STRENGTH,
    DEXTERITY,
    CONSTITUTION,
    INTELLIGENCE,
    WISDOM,
    CHARISMA;

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

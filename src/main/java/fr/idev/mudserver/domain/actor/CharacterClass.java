package fr.idev.mudserver.domain.actor;

public enum CharacterClass {
    BARBARIAN, BARD, CLERIC, DRUID, FIGHTER, MONK, PALADIN, RANGER, ROGUE, SORCERER, WARLOCK, WIZARD;

    public String label() {
        return switch (this) {
            case BARBARIAN -> "Barbarian";
            case BARD -> "Bard";
            case CLERIC -> "Cleric";
            case DRUID -> "Druid";
            case FIGHTER -> "Fighter";
            case MONK -> "Monk";
            case PALADIN -> "Paladin";
            case RANGER -> "Ranger";
            case ROGUE -> "Rogue";
            case SORCERER -> "Sorcerer";
            case WARLOCK -> "Warlock";
            case WIZARD -> "Wizard";
        };
    }
}

package fr.idev.mudserver.domain.actor;

public enum Race {
    DWARF, HUMAN, HIGH_ELF, HALF_ORC, DRAGONBORN, ELF, GNOME, ROCK_GNOME, HALF_ELF, HALFLING, LIGHTFOOT_HALFLING, TIEFLING, HILL_DWARF;

    public String label() {
        return switch (this) {
            case DWARF -> "Dwarf";
            case HUMAN -> "Human";
            case HIGH_ELF -> "High Elf";
            case HALF_ORC -> "Half-Orc";
            case DRAGONBORN -> "Dragonborn";
            case ELF -> "Elf";
            case GNOME -> "Gnome";
            case ROCK_GNOME -> "Rock Gnome";
            case HALF_ELF -> "Half-Elf";
            case HALFLING -> "Halfling";
            case LIGHTFOOT_HALFLING -> "Lightfoot Halfling";
            case TIEFLING -> "Tiefling";
            case HILL_DWARF -> "Hill Dwarf";
        };
    }
}

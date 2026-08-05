package fr.idev.mudserver.domain.actor;

public enum Race {
    DWARF, HUMAN, HIGH_ELF, ORC;

    public String label() {
        return switch (this) {
            case DWARF -> "Dwarf";
            case HUMAN -> "Human";
            case HIGH_ELF -> "High Elf";
            case ORC -> "Orc";
        };
    }
}

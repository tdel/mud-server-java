package fr.idev.mudserver.domain;

import java.util.Map;

import static fr.idev.mudserver.domain.Ability.CHARISMA;
import static fr.idev.mudserver.domain.Ability.CONSTITUTION;
import static fr.idev.mudserver.domain.Ability.DEXTERITY;
import static fr.idev.mudserver.domain.Ability.INTELLIGENCE;
import static fr.idev.mudserver.domain.Ability.STRENGTH;
import static fr.idev.mudserver.domain.Ability.WISDOM;

public enum Race {
    DWARF,
    HUMAN,
    HIGH_ELF,
    ORC;

    public String label() {
        return switch (this) {
            case DWARF -> "Dwarf";
            case HUMAN -> "Human";
            case HIGH_ELF -> "High Elf";
            case ORC -> "Orc";
        };
    }

    public Map<Ability, Integer> abilityScoreBonuses() {
        return switch (this) {
            case DWARF -> Map.of(STRENGTH, 2, CONSTITUTION, 2);
            case HUMAN -> Map.of(
                    STRENGTH, 1, DEXTERITY, 1, CONSTITUTION, 1,
                    INTELLIGENCE, 1, WISDOM, 1, CHARISMA, 1);
            case HIGH_ELF -> Map.of(DEXTERITY, 2, INTELLIGENCE, 2, WISDOM, 1, CHARISMA, 1);
            case ORC -> Map.of(STRENGTH, 2, DEXTERITY, 1, WISDOM, 1);
        };
    }
}

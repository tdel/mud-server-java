package fr.idev.mudserver.domain;

import java.util.LinkedHashMap;
import java.util.Map;

import static fr.idev.mudserver.domain.Ability.CHARISMA;
import static fr.idev.mudserver.domain.Ability.CONSTITUTION;
import static fr.idev.mudserver.domain.Ability.DEXTERITY;
import static fr.idev.mudserver.domain.Ability.INTELLIGENCE;
import static fr.idev.mudserver.domain.Ability.STRENGTH;
import static fr.idev.mudserver.domain.Ability.WISDOM;

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

    /**
     * {@code LinkedHashMap} plutôt que {@code Map.of(...)} : l'ordre d'insertion
     * doit rester stable (affiché tel quel par {@code ChooseRace}), ce que
     * {@code Map.of(...)} ne garantit pas (ordre d'itération volontairement
     * randomisé par la JVM).
     */
    public Map<Ability, Integer> abilityScoreBonuses() {
        Map<Ability, Integer> bonuses = new LinkedHashMap<>();
        switch (this) {
            case DWARF -> {
                bonuses.put(STRENGTH, 2);
                bonuses.put(CONSTITUTION, 2);
            }
            case HUMAN -> {
                bonuses.put(STRENGTH, 1);
                bonuses.put(DEXTERITY, 1);
                bonuses.put(CONSTITUTION, 1);
                bonuses.put(INTELLIGENCE, 1);
                bonuses.put(WISDOM, 1);
                bonuses.put(CHARISMA, 1);
            }
            case HIGH_ELF -> {
                bonuses.put(DEXTERITY, 2);
                bonuses.put(INTELLIGENCE, 2);
                bonuses.put(WISDOM, 1);
                bonuses.put(CHARISMA, 1);
            }
            case ORC -> {
                bonuses.put(STRENGTH, 2);
                bonuses.put(DEXTERITY, 1);
                bonuses.put(WISDOM, 1);
            }
        }
        return bonuses;
    }
}

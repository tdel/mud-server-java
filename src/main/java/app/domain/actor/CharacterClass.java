package app.domain.actor;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import app.domain.item.WeaponCategory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public enum CharacterClass {
    BARBARIAN, BARD, CLERIC, DRUID, FIGHTER, MONK, PALADIN, RANGER, ROGUE, SORCERER, WARLOCK, WIZARD;

    private static final String RESOURCE = "/data/class.json";

    static {
        try (InputStream in = CharacterClass.class.getResourceAsStream(RESOURCE)) {
            ObjectMapper objectMapper = new ObjectMapper();
            List<Json> definitions = objectMapper.readValue(in, new TypeReference<List<Json>>() {
            });
            for (Json json : definitions) {
                json.name().definition = json.toDefinition();
            }
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + RESOURCE, e);
        }
    }

    private Definition definition;

    public int hitDie() {
        return definition.hitDie();
    }

    public StartingGold startingGold() {
        return definition.startingGold();
    }

    public Set<Attribute> savingThrowProficiencies() {
        return definition.savingThrows();
    }

    public Set<Skill> skillProficiencies() {
        return definition.skills();
    }

    public Attribute primaryAbility() {
        return definition.primaryAbility();
    }

    public Set<WeaponCategory> weaponProficiencies() {
        return definition.weaponProficiencies();
    }

    public Set<ArmorProficiency> armorProficiencies() {
        return definition.armorProficiencies();
    }

    public int manaGainPerLevel() {
        return definition.manaGainPerLevel();
    }

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

    public record StartingGold(String dice, int multiplier) {
    }

    private record Definition(int hitDie, StartingGold startingGold, Set<Attribute> savingThrows, Set<Skill> skills,
            Attribute primaryAbility, Set<WeaponCategory> weaponProficiencies, Set<ArmorProficiency> armorProficiencies,
            int manaGainPerLevel) {
    }

    private record Json(CharacterClass name, int hitDie, String startingGoldDice, int startingGoldMultiplier,
            List<Attribute> savingThrows, List<Skill> skills, Attribute primaryAbility,
            List<WeaponCategory> weaponProficiencies, List<ArmorProficiency> armorProficiencies, int manaGainPerLevel) {
        Definition toDefinition() {
            return new Definition(hitDie, new StartingGold(startingGoldDice, startingGoldMultiplier),
                    Set.copyOf(savingThrows), Set.copyOf(skills), primaryAbility, Set.copyOf(weaponProficiencies),
                    Set.copyOf(armorProficiencies), manaGainPerLevel);
        }
    }
}

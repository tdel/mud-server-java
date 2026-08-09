package fr.idev.mudserver.game.actor;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.ArmorProficiency;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Skill;
import fr.idev.mudserver.domain.WeaponCategory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Précharge les données de règles par classe depuis {@code data/class.json}
 * (voir {@link #warmClassDefinitions()}), sur le même principe que
 * {@code RaceService.warmRaceBonuses()} : donnée de règles statique, jamais
 * mutée en jeu, chargée depuis le classpath plutôt que la DB.
 */
@Service
public class ClassService {

    private static final String CLASS_RESOURCE = "/data/class.json";

    private final Map<CharacterClass, ClassDefinition> definitionsByClass = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public ClassService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void warmClassDefinitions() {
        try (InputStream in = getClass().getResourceAsStream(CLASS_RESOURCE)) {
            List<ClassDefinition> definitions = objectMapper.readValue(in, new TypeReference<List<ClassDefinition>>() {
            });
            for (ClassDefinition definition : definitions) {
                definitionsByClass.put(definition.name(), definition);
            }
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + CLASS_RESOURCE, e);
        }
    }

    public int hitDie(CharacterClass characterClass) {
        return definition(characterClass).hitDie();
    }

    public StartingGold startingGold(CharacterClass characterClass) {
        ClassDefinition definition = definition(characterClass);
        return new StartingGold(definition.startingGoldDice(), definition.startingGoldMultiplier());
    }

    public Set<Attribute> savingThrowProficiencies(CharacterClass characterClass) {
        return Set.copyOf(definition(characterClass).savingThrows());
    }

    public Set<Skill> skillProficiencies(CharacterClass characterClass) {
        return Set.copyOf(definition(characterClass).skills());
    }

    public Attribute primaryAbility(CharacterClass characterClass) {
        return definition(characterClass).primaryAbility();
    }

    public Set<WeaponCategory> weaponProficiencies(CharacterClass characterClass) {
        return Set.copyOf(definition(characterClass).weaponProficiencies());
    }

    public Set<ArmorProficiency> armorProficiencies(CharacterClass characterClass) {
        return Set.copyOf(definition(characterClass).armorProficiencies());
    }

    private ClassDefinition definition(CharacterClass characterClass) {
        ClassDefinition definition = definitionsByClass.get(characterClass);
        if (definition == null) {
            throw new IllegalStateException("Définition de " + characterClass
                    + " absente du cache — warmClassDefinitions() a-t-il été appelé ?");
        }
        return definition;
    }

    public record StartingGold(String dice, int multiplier) {
    }

    private record ClassDefinition(CharacterClass name, int hitDie, String startingGoldDice, int startingGoldMultiplier,
            List<Attribute> savingThrows, List<Skill> skills, Attribute primaryAbility,
            List<WeaponCategory> weaponProficiencies, List<ArmorProficiency> armorProficiencies) {
    }
}

package fr.idev.mudserver.game.actor;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.CharacterClass;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Précharge le dé de vie de chaque classe depuis {@code data/class.json} (voir
 * {@link #warmClassHitDice()}), sur le même principe que
 * {@code RaceService.warmRaceBonuses()} : donnée de règles statique, jamais
 * mutée en jeu, chargée depuis le classpath plutôt que la DB.
 */
@Service
public class ClassService {

    private static final String CLASS_RESOURCE = "/data/class.json";

    private final Map<CharacterClass, Integer> hitDiceByClass = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public ClassService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void warmClassHitDice() {
        try (InputStream in = getClass().getResourceAsStream(CLASS_RESOURCE)) {
            List<ClassDefinition> definitions = objectMapper.readValue(in, new TypeReference<List<ClassDefinition>>() {
            });
            for (ClassDefinition definition : definitions) {
                hitDiceByClass.put(definition.name(), definition.hitDie());
            }
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + CLASS_RESOURCE, e);
        }
    }

    public int hitDie(CharacterClass characterClass) {
        Integer hitDie = hitDiceByClass.get(characterClass);
        if (hitDie == null) {
            throw new IllegalStateException(
                    "Dé de vie de " + characterClass + " absent du cache — warmClassHitDice() a-t-il été appelé ?");
        }
        return hitDie;
    }

    private record ClassDefinition(CharacterClass name, int hitDie) {
    }
}

package fr.idev.mudserver.game;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Précharge la table officielle des points d'expérience requis par niveau
 * depuis {@code data/levels.json} (voir {@link #warmXpThresholds()}), sur le
 * même principe que {@code RaceService.warmRaceBonuses()}/
 * {@code ClassService.warmClassHitDice()} : donnée de règles statique, jamais
 * mutée en jeu, chargée depuis le classpath plutôt que la DB.
 */
@Service
public class LevelService {

    private static final String LEVELS_RESOURCE = "/data/levels.json";

    private final Map<Integer, Integer> xpRequiredByLevel = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public LevelService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void warmXpThresholds() {
        try (InputStream in = getClass().getResourceAsStream(LEVELS_RESOURCE)) {
            List<LevelDefinition> definitions = objectMapper.readValue(in, new TypeReference<List<LevelDefinition>>() {
            });
            for (LevelDefinition definition : definitions) {
                xpRequiredByLevel.put(definition.level(), definition.xp());
            }
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + LEVELS_RESOURCE, e);
        }
    }

    public int xpRequiredForLevel(int level) {
        Integer xp = xpRequiredByLevel.get(level);
        if (xp == null) {
            throw new IllegalStateException(
                    "XP requis pour le niveau " + level + " absent du cache — warmXpThresholds() a-t-il été appelé ?");
        }
        return xp;
    }

    private record LevelDefinition(int level, int xp) {
    }
}

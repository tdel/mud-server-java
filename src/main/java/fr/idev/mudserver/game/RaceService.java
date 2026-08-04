package fr.idev.mudserver.game;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.Attribute;
import fr.idev.mudserver.domain.Race;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Précharge les bonus/malus raciaux depuis {@code data/race.json} (voir
 * {@link #warmRaceBonuses()}), sur le même principe que
 * {@code ItemService.warmItemTemplates()} — sauf que la source est un fichier
 * JSON du classpath, pas la DB : les bonus raciaux sont une donnée de règles
 * statique, jamais mutée en jeu.
 */
@Service
public class RaceService {

    private static final String RACE_RESOURCE = "/data/race.json";

    private final Map<Race, Map<Attribute, Integer>> attributeScoreBonuses = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RaceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void warmRaceBonuses() {
        try (InputStream in = getClass().getResourceAsStream(RACE_RESOURCE)) {
            List<RaceDefinition> definitions = objectMapper.readValue(in, new TypeReference<List<RaceDefinition>>() {
            });
            for (RaceDefinition definition : definitions) {
                attributeScoreBonuses.put(definition.name(), Collections.unmodifiableMap(definition.attributes()));
            }
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + RACE_RESOURCE, e);
        }
    }

    public Map<Attribute, Integer> attributeScoreBonuses(Race race) {
        Map<Attribute, Integer> bonuses = attributeScoreBonuses.get(race);
        if (bonuses == null) {
            throw new IllegalStateException(
                    "Bonus de " + race + " absents du cache — warmRaceBonuses() a-t-il été appelé ?");
        }
        return bonuses;
    }

    private record RaceDefinition(Race name, Map<Attribute, Integer> attributes) {
    }
}

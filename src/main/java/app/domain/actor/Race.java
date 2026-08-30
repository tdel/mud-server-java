package app.domain.actor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public enum Race {
    HUMAN;

    private static final String RESOURCE = "/data/race.json";

    static {
        try (InputStream in = Race.class.getResourceAsStream(RESOURCE)) {
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

    public Map<Attribute, Integer> attributeScoreBonuses() {
        return definition.attributes();
    }

    public int speed() {
        return definition.speed();
    }

    public String label() {
        return switch (this) {
            case HUMAN -> "Human";
        };
    }

    private record Definition(Map<Attribute, Integer> attributes, int speed) {
        private Definition {
            attributes = Collections.unmodifiableMap(attributes);
        }
    }

    private record Json(Race name, Map<Attribute, Integer> attributes, int speed) {
        Definition toDefinition() {
            return new Definition(attributes, speed);
        }
    }
}

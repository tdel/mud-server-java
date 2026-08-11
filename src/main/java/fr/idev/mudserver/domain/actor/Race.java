package fr.idev.mudserver.domain.actor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public enum Race {
    DWARF, HUMAN, HIGH_ELF, HALF_ORC, DRAGONBORN, ELF, GNOME, ROCK_GNOME, HALF_ELF, HALFLING, LIGHTFOOT_HALFLING, TIEFLING, HILL_DWARF;

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

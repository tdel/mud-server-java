package app.domain.actor;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.dataformat.xml.XmlMapper;

public enum Race {
    HUMAN;

    private static final String RESOURCE = "/data/race.xml";

    static {
        try (InputStream in = Race.class.getResourceAsStream(RESOURCE)) {
            XmlMapper xmlMapper = new XmlMapper();
            List<Json> definitions = xmlMapper.readValue(in, new TypeReference<List<Json>>() {
            });
            for (Race race : values()) {
                if (definitions.stream().noneMatch(json -> json.name() == race)) {
                    throw new IllegalStateException("Race " + race + " absente de " + RESOURCE);
                }
            }
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + RESOURCE, e);
        }
    }

    // Vitesse fixe par race : les stats de personnage viennent uniquement de la
    // classe (cf. data/classes/*.xml), la race ne sert plus qu'à identifier le
    // joueur pour le moment (human-only).
    public int speed() {
        return switch (this) {
            case HUMAN -> 110;
        };
    }

    public String label() {
        return switch (this) {
            case HUMAN -> "Human";
        };
    }

    private record Json(Race name) {
    }
}

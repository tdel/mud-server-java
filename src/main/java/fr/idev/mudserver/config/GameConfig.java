package fr.idev.mudserver.config;

import java.io.IOException;
import java.io.InputStream;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class GameConfig {

    private static final String RESOURCE = "/config.json";

    public static final int LONG_REST_PROVISION_THRESHOLD;

    static {
        try (InputStream in = GameConfig.class.getResourceAsStream(RESOURCE)) {
            ObjectMapper objectMapper = new ObjectMapper();
            Json json = objectMapper.readValue(in, Json.class);
            LONG_REST_PROVISION_THRESHOLD = json.longRestProvisionThreshold();
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + RESOURCE, e);
        }
    }

    private GameConfig() {
    }

    private record Json(int longRestProvisionThreshold) {
    }
}

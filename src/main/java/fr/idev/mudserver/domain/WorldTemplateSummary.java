package fr.idev.mudserver.domain;

import java.util.UUID;

public record WorldTemplateSummary(UUID id, String shortName, String name, String description, int minPlayers,
        int maxPlayers) {
}

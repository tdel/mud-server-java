package fr.idev.mudserver.domain;

import java.util.UUID;

public record ItemTemplate(
        UUID id,
        String name,
        String description,
        ItemType type,
        int weight
) {
}

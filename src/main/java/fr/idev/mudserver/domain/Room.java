package fr.idev.mudserver.domain;

import java.util.UUID;

/**
 * {@code isStartingRoom} is a nullable sentinel, not a plain boolean: NULL/TRUE only,
 * never FALSE (see V1__init_schema.sql's uniq_room_starting index).
 */
public record Room(
        UUID id,
        String name,
        String description,
        Boolean isStartingRoom
) {
}

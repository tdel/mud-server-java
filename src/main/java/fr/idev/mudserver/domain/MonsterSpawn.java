package fr.idev.mudserver.domain;

import java.util.UUID;

public record MonsterSpawn(UUID id, UUID templateId, HexCoordinate cell) {
}

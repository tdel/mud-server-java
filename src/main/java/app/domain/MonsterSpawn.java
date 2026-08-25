package app.domain;

import app.domain.map.Position;

import java.util.UUID;

public record MonsterSpawn(UUID id, UUID templateId, Position position) {
}

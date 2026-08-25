package app.domain;

import app.domain.map.Position;

import java.util.UUID;

public record NpcSpawn(UUID id, UUID npcId, Position position) {
}

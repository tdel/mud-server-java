package fr.idev.mudserver.domain;

import fr.idev.mudserver.domain.map.Position;

import java.util.UUID;

public record NpcSpawn(UUID id, UUID npcId, Position position) {
}

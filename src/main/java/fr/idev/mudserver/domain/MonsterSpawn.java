package fr.idev.mudserver.domain;

import fr.idev.mudserver.domain.map.Position;

import java.util.UUID;

public record MonsterSpawn(UUID id, UUID templateId, Position position) {
}

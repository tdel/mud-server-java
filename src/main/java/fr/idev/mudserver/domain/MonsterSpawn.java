package fr.idev.mudserver.domain;

import fr.idev.mudserver.domain.map.HexCoordinate;

import java.util.UUID;

public record MonsterSpawn(UUID id, UUID templateId, HexCoordinate cell) {
}

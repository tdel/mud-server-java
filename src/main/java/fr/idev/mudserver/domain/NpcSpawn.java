package fr.idev.mudserver.domain;

import fr.idev.mudserver.domain.map.HexCoordinate;

import java.util.UUID;

public record NpcSpawn(UUID id, UUID npcId, HexCoordinate cell) {
}

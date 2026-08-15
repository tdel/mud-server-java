package fr.idev.mudserver.domain.actor;

import java.util.UUID;

import fr.idev.mudserver.domain.HexCoordinate;

public record NpcTemplate(UUID id, String name, UUID roomTemplateId, HexCoordinate cell, String description,
        GameNpc.NpcDialogue dialogue, GameNpcSeller.NpcShop shop, int level) {
}

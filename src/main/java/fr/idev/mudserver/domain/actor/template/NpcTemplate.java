package fr.idev.mudserver.domain.actor.template;

import java.util.UUID;

import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.actor.instance.NpcSellerInstance;

public record NpcTemplate(UUID id, String name, UUID roomTemplateId, HexCoordinate cell, String description,
        AbstractNpc.NpcDialogue dialogue, NpcSellerInstance.NpcShop shop, int level) {
}

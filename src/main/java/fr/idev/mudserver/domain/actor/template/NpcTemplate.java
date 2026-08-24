package fr.idev.mudserver.domain.actor.template;

import java.util.UUID;

import fr.idev.mudserver.domain.map.Position;
import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.actor.instance.NpcSellerInstance;

public record NpcTemplate(UUID id, String name, UUID zoneTemplateId, Position position, String description,
        AbstractNpc.NpcDialogue dialogue, NpcSellerInstance.NpcShop shop, int level) {
}

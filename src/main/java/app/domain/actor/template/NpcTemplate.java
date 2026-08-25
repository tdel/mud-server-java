package app.domain.actor.template;

import java.util.UUID;

import app.domain.map.Position;
import app.domain.actor.AbstractNpc;
import app.domain.actor.instance.NpcSellerInstance;

public record NpcTemplate(UUID id, String name, UUID zoneTemplateId, Position position, String description,
        AbstractNpc.NpcDialogue dialogue, NpcSellerInstance.NpcShop shop, int level) {
}

package fr.idev.mudserver.domain.actor.template;

import java.util.UUID;

import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.actor.component.DialogueComponent;
import fr.idev.mudserver.domain.actor.component.NpcDescriptorComponent;
import fr.idev.mudserver.domain.actor.component.ShopComponent;

public record NpcTemplate(UUID id, String name, UUID roomTemplateId, HexCoordinate cell,
        NpcDescriptorComponent descriptor, DialogueComponent dialogue, ShopComponent shop) {
}

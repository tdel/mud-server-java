package fr.idev.mudserver.domain.actor;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import fr.idev.mudserver.domain.actor.component.NpcDescriptorComponent;
import fr.idev.mudserver.domain.actor.component.PositionComponent;
import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.domain.actor.template.NpcTemplate;

public class AbstractNpc extends AbstractCharacter {

    private static final int NOMINAL_HEALTH = 1;

    public AbstractNpc(UUID id, NpcTemplate template, RoomInstance room) {
        super(id, template.name(), neutralAttributes(), NOMINAL_HEALTH, NOMINAL_HEALTH, 0);
        attachComponent(new PositionComponent(room, template.cell()));
        attachComponent(template.descriptor());
        if (template.dialogue() != null) {
            attachComponent(template.dialogue());
        }
    }

    private static Map<Attribute, Integer> neutralAttributes() {
        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.values()) {
            attributes.put(attribute, 10);
        }
        return attributes;
    }
}

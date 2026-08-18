package fr.idev.mudserver.game.catalog;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.idev.mudserver.game.ECS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.domain.world.WorldTemplate;
import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.component.AttributeComponent;
import fr.idev.mudserver.domain.actor.component.CombatComponent;
import fr.idev.mudserver.domain.actor.component.HealthComponent;
import fr.idev.mudserver.domain.actor.component.IdentityComponent;
import fr.idev.mudserver.domain.actor.component.PositionComponent;
import fr.idev.mudserver.domain.actor.instance.NpcSellerInstance;
import fr.idev.mudserver.domain.actor.template.NpcTemplate;

@Service
public class NpcCatalog {

    private static final Logger log = LoggerFactory.getLogger(NpcCatalog.class);

    private static final int NPC_NOMINAL_HEALTH = 1;

    private final ECS ecs;

    public NpcCatalog(ECS ecs) {
        this.ecs = ecs;
    }

    public void warmNpcs(Collection<WorldTemplate> worldTemplates, Collection<RoomInstance> rooms) {
        Map<UUID, RoomInstance> roomsByTemplateId = new ConcurrentHashMap<>();
        for (RoomInstance room : rooms) {
            roomsByTemplateId.put(room.getTemplateId(), room);
        }

        int count = 0;
        for (WorldTemplate worldTemplate : worldTemplates) {
            for (NpcTemplate template : worldTemplate.getNpcTemplates().values()) {
                RoomInstance room = roomsByTemplateId.get(template.roomTemplateId());
                if (room == null) {
                    throw new IllegalStateException("NPC " + template.id() + " référence la room "
                            + template.roomTemplateId() + ", absente du monde " + worldTemplate.getShortName());
                }
                place(template, room);
                count++;
            }
        }

        log.info("npc.instances_placed count={}", count);
    }

    private void place(NpcTemplate template, RoomInstance room) {
        AbstractNpc npc = template.shop() != null
                ? new NpcSellerInstance(template.id(), ecs)
                : new AbstractNpc(template.id(), ecs);

        npc.attachComponent(new IdentityComponent(template.name(), 0));
        npc.attachComponent(new AttributeComponent(neutralAttributes()));
        npc.attachComponent(new HealthComponent(NPC_NOMINAL_HEALTH, NPC_NOMINAL_HEALTH));
        npc.attachComponent(new CombatComponent(null, CombatComponent.DEFAULT_ACTIONS_MAX,
                CombatComponent.DEFAULT_EXTRA_ACTIONS_MAX, CombatComponent.DEFAULT_ACTIONS_MAX,
                CombatComponent.DEFAULT_EXTRA_ACTIONS_MAX));
        npc.attachComponent(new PositionComponent(room, template.cell()));
        npc.attachComponent(template.descriptor());
        if (template.dialogue() != null) {
            npc.attachComponent(template.dialogue());
        }
        if (npc instanceof NpcSellerInstance) {
            npc.attachComponent(Objects.requireNonNull(template.shop()));
        }

        room.placeNpc(npc, template.cell());
        ecs.register(npc);
    }

    private static Map<Attribute, Integer> neutralAttributes() {
        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.values()) {
            attributes.put(attribute, 10);
        }
        return attributes;
    }
}

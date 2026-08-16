package fr.idev.mudserver.game.catalog;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.domain.world.WorldTemplate;
import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.actor.instance.NpcSellerInstance;
import fr.idev.mudserver.domain.actor.template.NpcTemplate;

@Service
public class NpcCatalog {

    private static final Logger log = LoggerFactory.getLogger(NpcCatalog.class);

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
                ? new NpcSellerInstance(template.id(), template, room)
                : new AbstractNpc(template.id(), template, room);
        room.placeNpc(npc, template.cell());
    }
}

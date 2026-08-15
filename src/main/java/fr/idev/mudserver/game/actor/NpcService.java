package fr.idev.mudserver.game.actor;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.WorldTemplate;
import fr.idev.mudserver.domain.actor.GameNpc;
import fr.idev.mudserver.domain.actor.GameNpcSeller;
import fr.idev.mudserver.domain.actor.NpcTemplate;

@Service
public class NpcService {

    private static final Logger log = LoggerFactory.getLogger(NpcService.class);

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
        GameNpc npc = template.shop() != null
                ? new GameNpcSeller(template.id(), template.name(), room.getId(), template.description(),
                        template.dialogue(), template.shop(), template.level())
                : new GameNpc(template.id(), template.name(), room.getId(), template.description(), template.dialogue(),
                        template.level());
        npc.setCurrentRoom(room);
        room.placeNpc(npc, template.cell());
    }
}

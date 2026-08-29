package app.game.catalog;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import app.domain.world.MapInstance;
import app.domain.world.WorldTemplate;
import app.domain.actor.AbstractNpc;
import app.domain.actor.instance.NpcSellerInstance;
import app.domain.actor.template.NpcTemplate;

@Service
public class NpcCatalog {

    private static final Logger log = LoggerFactory.getLogger(NpcCatalog.class);

    public void warmNpcs(Collection<WorldTemplate> worldTemplates, Collection<MapInstance> maps) {
        Map<UUID, MapInstance> mapsByTemplateId = new ConcurrentHashMap<>();
        for (MapInstance map : maps) {
            mapsByTemplateId.put(map.getTemplateId(), map);
        }

        int count = 0;
        for (WorldTemplate worldTemplate : worldTemplates) {
            for (NpcTemplate template : worldTemplate.getNpcTemplates().values()) {
                MapInstance map = mapsByTemplateId.get(template.mapTemplateId());
                if (map == null) {
                    throw new IllegalStateException("NPC " + template.id() + " référence la map "
                            + template.mapTemplateId() + ", absente du monde " + worldTemplate.getShortName());
                }
                place(template, map);
                count++;
            }
        }

        log.info("npc.instances_placed count={}", count);
    }

    private void place(NpcTemplate template, MapInstance map) {
        AbstractNpc npc = template.shop() != null
                ? new NpcSellerInstance(template.id(), template, map)
                : new AbstractNpc(template.id(), template, map);
        map.placeNpc(npc, template.position());
    }
}

package app.game.catalog;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import app.domain.world.ZoneInstance;
import app.domain.world.WorldTemplate;
import app.domain.actor.AbstractNpc;
import app.domain.actor.instance.NpcSellerInstance;
import app.domain.actor.template.NpcTemplate;

@Service
public class NpcCatalog {

    private static final Logger log = LoggerFactory.getLogger(NpcCatalog.class);

    public void warmNpcs(Collection<WorldTemplate> worldTemplates, Collection<ZoneInstance> zones) {
        Map<UUID, ZoneInstance> zonesByTemplateId = new ConcurrentHashMap<>();
        for (ZoneInstance zone : zones) {
            zonesByTemplateId.put(zone.getTemplateId(), zone);
        }

        int count = 0;
        for (WorldTemplate worldTemplate : worldTemplates) {
            for (NpcTemplate template : worldTemplate.getNpcTemplates().values()) {
                ZoneInstance zone = zonesByTemplateId.get(template.zoneTemplateId());
                if (zone == null) {
                    throw new IllegalStateException("NPC " + template.id() + " référence la zone "
                            + template.zoneTemplateId() + ", absente du monde " + worldTemplate.getShortName());
                }
                place(template, zone);
                count++;
            }
        }

        log.info("npc.instances_placed count={}", count);
    }

    private void place(NpcTemplate template, ZoneInstance zone) {
        AbstractNpc npc = template.shop() != null
                ? new NpcSellerInstance(template.id(), template, zone)
                : new AbstractNpc(template.id(), template, zone);
        zone.placeNpc(npc, template.position());
    }
}

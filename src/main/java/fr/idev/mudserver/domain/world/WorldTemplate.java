package fr.idev.mudserver.domain.world;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import fr.idev.mudserver.domain.actor.template.NpcTemplate;

public class WorldTemplate {

    private final UUID id;
    private final String shortName;
    private final String name;
    private final String description;
    private final int minPlayers;
    private final int maxPlayers;
    private final Map<UUID, ZoneTemplate> zoneTemplates;
    private final Map<UUID, NpcTemplate> npcTemplates;

    public WorldTemplate(UUID id, String shortName, String name, String description, int minPlayers, int maxPlayers,
            Map<UUID, ZoneTemplate> zoneTemplates, Map<UUID, NpcTemplate> npcTemplates) {
        this.id = id;
        this.shortName = shortName;
        this.name = name;
        this.description = description;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.zoneTemplates = Map.copyOf(zoneTemplates);
        this.npcTemplates = Map.copyOf(npcTemplates);
    }

    public UUID getId() {
        return id;
    }

    public String getShortName() {
        return shortName;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public Map<UUID, ZoneTemplate> getZoneTemplates() {
        return zoneTemplates;
    }

    public Map<UUID, NpcTemplate> getNpcTemplates() {
        return npcTemplates;
    }

    public Optional<ZoneTemplate> startingZoneTemplate() {
        return zoneTemplates.values().stream().filter(zone -> Boolean.TRUE.equals(zone.isStartingZone())).findFirst();
    }

    public Collection<NpcTemplate> npcTemplatesForZone(UUID zoneTemplateId) {
        return npcTemplates.values().stream().filter(npc -> npc.zoneTemplateId().equals(zoneTemplateId)).toList();
    }

    @Override
    public String toString() {
        return "WorldTemplate[id=" + id + ", shortName=" + shortName + ", name=" + name + ", minPlayers=" + minPlayers
                + ", maxPlayers=" + maxPlayers + ", zones=" + zoneTemplates.size() + ", npcs=" + npcTemplates.size()
                + "]";
    }
}

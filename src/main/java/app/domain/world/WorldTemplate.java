package app.domain.world;

import java.util.Map;
import java.util.UUID;

import app.domain.actor.template.NpcTemplate;

public class WorldTemplate {

    private final UUID id;
    private final String shortName;
    private final String name;
    private final String description;
    private final int minPlayers;
    private final int maxPlayers;
    private final Map<UUID, MapTemplate> mapTemplates;
    private final Map<UUID, NpcTemplate> npcTemplates;

    public WorldTemplate(UUID id, String shortName, String name, String description, int minPlayers, int maxPlayers,
            Map<UUID, MapTemplate> mapTemplates, Map<UUID, NpcTemplate> npcTemplates) {
        this.id = id;
        this.shortName = shortName;
        this.name = name;
        this.description = description;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.mapTemplates = Map.copyOf(mapTemplates);
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

    public Map<UUID, MapTemplate> getMapTemplates() {
        return mapTemplates;
    }

    public Map<UUID, NpcTemplate> getNpcTemplates() {
        return npcTemplates;
    }

    @Override
    public String toString() {
        return "WorldTemplate[id=" + id + ", shortName=" + shortName + ", name=" + name + ", minPlayers=" + minPlayers
                + ", maxPlayers=" + maxPlayers + ", maps=" + mapTemplates.size() + ", npcs=" + npcTemplates.size()
                + "]";
    }
}

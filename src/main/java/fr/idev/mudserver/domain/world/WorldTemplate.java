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
    private final Map<UUID, RoomTemplate> roomTemplates;
    private final Map<UUID, NpcTemplate> npcTemplates;

    public WorldTemplate(UUID id, String shortName, String name, String description, int minPlayers, int maxPlayers,
            Map<UUID, RoomTemplate> roomTemplates, Map<UUID, NpcTemplate> npcTemplates) {
        this.id = id;
        this.shortName = shortName;
        this.name = name;
        this.description = description;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.roomTemplates = Map.copyOf(roomTemplates);
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

    public Map<UUID, RoomTemplate> getRoomTemplates() {
        return roomTemplates;
    }

    public Map<UUID, NpcTemplate> getNpcTemplates() {
        return npcTemplates;
    }

    public Optional<RoomTemplate> startingRoomTemplate() {
        return roomTemplates.values().stream().filter(room -> Boolean.TRUE.equals(room.isStartingRoom())).findFirst();
    }

    public Collection<NpcTemplate> npcTemplatesForRoom(UUID roomTemplateId) {
        return npcTemplates.values().stream().filter(npc -> npc.roomTemplateId().equals(roomTemplateId)).toList();
    }

    @Override
    public String toString() {
        return "WorldTemplate[id=" + id + ", shortName=" + shortName + ", name=" + name + ", minPlayers=" + minPlayers
                + ", maxPlayers=" + maxPlayers + ", rooms=" + roomTemplates.size() + ", npcs=" + npcTemplates.size()
                + "]";
    }
}

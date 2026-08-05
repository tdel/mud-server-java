package fr.idev.mudserver.game.actor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.GameNpc;
import fr.idev.mudserver.domain.Room;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Précharge les PNJ depuis {@code data/npcs.json}, sur le même principe que
 * {@code RoomService.warmRooms()} : contrairement à {@code MonsterService}, pas
 * de split template/instance ici — un NPC n'est qu'un nom et une room, rien à
 * dédupliquer entre instances — donc chaque entrée de {@code npcs.json} est
 * déjà une instance placée, comme {@code data/rooms.json}.
 */
@Service
public class NpcService {

    private static final String NPCS_RESOURCE = "/data/npcs.json";

    private final ObjectMapper objectMapper;

    public NpcService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void warmNpcs(Collection<Room> rooms) {
        try (InputStream in = getClass().getResourceAsStream(NPCS_RESOURCE)) {
            List<NpcDefinition> definitions = objectMapper.readValue(in, new TypeReference<List<NpcDefinition>>() {
            });
            loadNpcs(definitions, rooms);
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + NPCS_RESOURCE, e);
        }
    }

    void loadNpcs(List<NpcDefinition> definitions, Collection<Room> rooms) {
        Map<UUID, Room> roomsById = new ConcurrentHashMap<>();
        for (Room room : rooms) {
            roomsById.put(room.getId(), room);
        }

        for (NpcDefinition definition : definitions) {
            Room room = roomsById.get(definition.roomId());
            if (room == null) {
                throw new IllegalStateException("NPC " + definition.id() + " référence la room " + definition.roomId()
                        + ", absente de data/rooms.json");
            }

            GameNpc npc = new GameNpc(definition.id(), definition.name(), room.getId());
            npc.setCurrentRoom(room);
            room.addNpc(npc);
        }
    }

    record NpcDefinition(UUID id, String name, UUID roomId) {
    }
}

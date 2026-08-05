package fr.idev.mudserver.game.actor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.MonsterTemplate;
import fr.idev.mudserver.domain.Room;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Précharge les monstres depuis {@code data/monsters.json}, sur le même
 * principe que {@code ItemService}/{@code RoomService} : donnée de contenu
 * statique, jamais mutée en jeu, chargée depuis le classpath plutôt que la DB —
 * pas de table monstre dans {@code V1__init_schema.sql}. Contrairement à
 * {@code ItemService}, templates et instances (« spawns ») partagent le même
 * fichier, donc une seule méthode/lecture ({@link #warmMonsters}) plutôt que le
 * split {@code warmItemTemplates()}/{@code warmRoomItems()}.
 */
@Service
public class MonsterService {

    private static final String MONSTERS_RESOURCE = "/data/monsters.json";

    private final Map<UUID, MonsterTemplate> templates = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public MonsterService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void warmMonsters(Collection<Room> rooms) {
        try (InputStream in = getClass().getResourceAsStream(MONSTERS_RESOURCE)) {
            MonsterFileDefinition file = objectMapper.readValue(in, MonsterFileDefinition.class);
            loadMonsters(file, rooms);
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + MONSTERS_RESOURCE, e);
        }
    }

    void loadMonsters(MonsterFileDefinition file, Collection<Room> rooms) {
        for (MonsterTemplateDefinition definition : file.templates()) {
            templates.put(definition.id(),
                    new MonsterTemplate(definition.id(), definition.name(), definition.description(),
                            definition.maxHealth(), definition.attributes(), definition.naturalArmorClass(),
                            definition.xpReward()));
        }

        Map<UUID, Room> roomsById = new ConcurrentHashMap<>();
        for (Room room : rooms) {
            roomsById.put(room.getId(), room);
        }

        for (MonsterSpawnDefinition spawn : file.spawns()) {
            MonsterTemplate template = templates.get(spawn.templateId());
            if (template == null) {
                throw new IllegalStateException("Spawn " + spawn.id() + " référence le template " + spawn.templateId()
                        + ", absent de " + MONSTERS_RESOURCE);
            }
            Room room = roomsById.get(spawn.roomId());
            if (room == null) {
                throw new IllegalStateException("Spawn " + spawn.id() + " référence la room " + spawn.roomId()
                        + ", absente de data/rooms.json");
            }

            GameMonster monster = new GameMonster(spawn.id(), template.getName(), template.getId(), room.getId(),
                    template.getAttributes(), template.getMaxHealth());
            monster.attachTemplate(template);
            monster.setCurrentRoom(room);
            room.addMonster(monster);
        }
    }

    record MonsterFileDefinition(List<MonsterTemplateDefinition> templates, List<MonsterSpawnDefinition> spawns) {
    }

    record MonsterTemplateDefinition(UUID id, String name, String description, int maxHealth,
            Map<Attribute, Integer> attributes, Integer naturalArmorClass, int xpReward) {
    }

    record MonsterSpawnDefinition(UUID id, UUID templateId, UUID roomId) {
    }
}

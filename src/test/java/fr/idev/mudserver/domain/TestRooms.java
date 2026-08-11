package fr.idev.mudserver.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@link RoomInstance} n'a plus de constructeur à base de champs scalaires
 * (name/description/width/height/spawnCell...) — il se compose désormais d'un
 * {@link RoomTemplate} et d'un {@link WorldInstance}, voir sa Javadoc. Ce
 * helper reconstitue une room de test autonome (son propre {@code RoomTemplate}
 * et son propre {@code WorldInstance} à un seul membre) pour les tests
 * unitaires qui n'ont pas besoin du graphe complet matérialisé par
 * {@code WorldInstanceService.materialize}.
 */
public final class TestRooms {

    private TestRooms() {
    }

    public static RoomInstance room(UUID id, String name, String description) {
        return room(id, name, description, List.of());
    }

    public static RoomInstance room(UUID id, String name, String description, List<MonsterSpawn> monsterSpawns) {
        return room(id, name, description, null, RoomTemplate.DEFAULT_WIDTH, RoomTemplate.DEFAULT_HEIGHT,
                new HexCoordinate(RoomTemplate.DEFAULT_WIDTH / 2, RoomTemplate.DEFAULT_HEIGHT / 2), monsterSpawns);
    }

    public static RoomInstance room(UUID id, String name, String description, Boolean isStartingRoom, int width,
            int height, HexCoordinate spawnCell) {
        return room(id, name, description, isStartingRoom, width, height, spawnCell, List.of());
    }

    public static RoomInstance room(UUID id, String name, String description, Boolean isStartingRoom, int width,
            int height, HexCoordinate spawnCell, List<MonsterSpawn> monsterSpawns) {
        RoomTemplate template = new RoomTemplate(id, name, description, isStartingRoom, width, height, spawnCell,
                monsterSpawns);
        WorldInstance worldInstance = new WorldInstance(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null,
                Set.of());
        RoomInstance instance = new RoomInstance(id, template, worldInstance);
        worldInstance.setRoomInstances(Map.of(template.getId(), instance));
        return instance;
    }

    /**
     * Deux rooms partageant le même {@link WorldInstance}, {@code source} portant
     * {@code portal} sur son template — nécessaire pour exercer
     * {@link RoomInstance#findPortalAt}/{@link RoomInstance#getPortals}, qui
     * résolvent la room cible via {@link WorldInstance#roomInstanceForTemplate}.
     * {@code portal.targetRoomTemplateId()} doit être {@code targetId}.
     */
    public static RoomInstance[] connectedByPortal(UUID sourceId, String sourceName, int width, int height,
            HexCoordinate sourceSpawnCell, RoomTemplatePortal portal, UUID targetId, String targetName) {
        RoomTemplate sourceTemplate = new RoomTemplate(sourceId, sourceName, "...", null, width, height,
                sourceSpawnCell, List.of());
        sourceTemplate.setPortals(List.of(portal));
        RoomTemplate targetTemplate = new RoomTemplate(targetId, targetName, "...", null, RoomTemplate.DEFAULT_WIDTH,
                RoomTemplate.DEFAULT_HEIGHT,
                new HexCoordinate(RoomTemplate.DEFAULT_WIDTH / 2, RoomTemplate.DEFAULT_HEIGHT / 2), List.of());

        WorldInstance worldInstance = new WorldInstance(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null,
                Set.of());
        RoomInstance source = new RoomInstance(sourceId, sourceTemplate, worldInstance);
        RoomInstance target = new RoomInstance(targetId, targetTemplate, worldInstance);
        worldInstance.setRoomInstances(Map.of(sourceId, source, targetId, target));
        return new RoomInstance[]{source, target};
    }
}

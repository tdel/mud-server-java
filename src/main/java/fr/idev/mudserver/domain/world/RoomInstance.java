package fr.idev.mudserver.domain.world;

import fr.idev.mudserver.domain.actor.component.IdentityComponent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import fr.idev.mudserver.domain.MonsterSpawn;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.component.NetworkComponent;
import fr.idev.mudserver.domain.actor.component.PositionComponent;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.GamePlayerSpawnedToRoom;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.network.message.ingame.GamePlayerDisconnected;
import fr.idev.mudserver.network.message.ingame.GamePlayerJoinedRoom;
import fr.idev.mudserver.network.message.ingame.GamePlayerLeftRoom;

public class RoomInstance {

    public static UUID deterministicId(UUID worldInstanceId, UUID roomTemplateId) {
        return UUID.nameUUIDFromBytes((worldInstanceId + ":" + roomTemplateId).getBytes(StandardCharsets.UTF_8));
    }

    private final UUID id;
    private final RoomTemplate template;
    private final WorldInstance worldInstance;

    private final Map<UUID, CharacterInstance> clients = new ConcurrentHashMap<>();
    private final List<MonsterInstance> monsters = new CopyOnWriteArrayList<>();
    private final List<AbstractNpc> npcs = new CopyOnWriteArrayList<>();
    private final Map<HexCoordinate, AbstractCharacter> occupants = new ConcurrentHashMap<>();

    public RoomInstance(UUID id, RoomTemplate template, WorldInstance worldInstance) {
        this.id = id;
        this.template = template;
        this.worldInstance = worldInstance;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTemplateId() {
        return template.getId();
    }

    public WorldInstance getWorldInstance() {
        return worldInstance;
    }

    public UUID getWorldInstanceId() {
        return worldInstance.getId();
    }

    public String getName() {
        return template.getName();
    }

    public String getDescription() {
        return template.getDescription();
    }

    public Boolean isStartingRoom() {
        return template.isStartingRoom();
    }

    public int getWidth() {
        return template.getWidth();
    }

    public int getHeight() {
        return template.getHeight();
    }

    public HexCoordinate getSpawnCell() {
        return template.getSpawnCell();
    }

    public boolean isInBounds(HexCoordinate cell) {
        return template.isInBounds(cell);
    }

    public boolean isBorderCell(HexCoordinate cell) {
        return template.isBorderCell(cell);
    }

    public void join(CharacterInstance character) {
        join(character, getSpawnCell());
    }

    public void join(CharacterInstance character, HexCoordinate cell) {
        HexCoordinate claimedCell = claimNearestFreeCell(cell, character);
        synchronized (character) {
            PositionComponent position = character.component(PositionComponent.class);
            position.currentRoom = this;
            position.hexCoordinate = claimedCell;
        }
        clients.put(character.getId(), character);
        DomainEventPublisher.publish(new GamePlayerSpawnedToRoom(character, this));

        broadcast(new GamePlayerJoinedRoom(character.component(IdentityComponent.class).name), character);
    }

    public void leave(CharacterInstance character) {
        clients.remove(character.getId());
        releaseCell(character.component(PositionComponent.class).hexCoordinate, character);
        broadcast(new GamePlayerLeftRoom(character.component(IdentityComponent.class).name), character);
    }

    public void disconnect(CharacterInstance character) {
        clients.remove(character.getId());
        releaseCell(character.component(PositionComponent.class).hexCoordinate, character);
        broadcast(new GamePlayerDisconnected(character.component(IdentityComponent.class).name), character);
    }

    public Optional<AbstractCharacter> findOccupantByName(String name) {
        List<AbstractCharacter> occupantsByName = new ArrayList<>();
        occupantsByName.addAll(clients.values());
        occupantsByName.addAll(monsters);
        occupantsByName.addAll(npcs);
        return occupantsByName.stream()
                .filter(occupant -> occupant.component(IdentityComponent.class).name.equalsIgnoreCase(name))
                .findFirst();
    }

    public List<CharacterInstance> characters() {
        return new ArrayList<>(clients.values());
    }

    public List<MonsterInstance> getMonsters() {
        return List.copyOf(monsters);
    }

    public void addMonster(MonsterInstance monster) {
        monsters.add(monster);
    }

    public void removeMonster(MonsterInstance monster) {
        monsters.remove(monster);
        releaseCell(monster.component(PositionComponent.class).hexCoordinate, monster);
    }

    public Optional<MonsterInstance> findMonsterByName(String name) {
        return monsters.stream()
                .filter(monster -> monster.component(IdentityComponent.class).name.equalsIgnoreCase(name)).findFirst();
    }

    public void setMonsters(List<MonsterInstance> monsters) {
        this.monsters.clear();
        this.monsters.addAll(monsters);
    }

    public List<MonsterSpawn> getMonsterSpawns() {
        return template.getMonsterSpawns();
    }

    public void placeMonster(MonsterInstance monster, HexCoordinate cell) {
        addMonster(monster);
        HexCoordinate claimedCell = claimNearestFreeCell(cell, monster);
        synchronized (monster) {
            monster.component(PositionComponent.class).hexCoordinate = claimedCell;
        }
    }

    public List<AbstractNpc> getNpcs() {
        return List.copyOf(npcs);
    }

    public void addNpc(AbstractNpc npc) {
        npcs.add(npc);
    }

    public void setNpcs(List<AbstractNpc> npcs) {
        this.npcs.clear();
        this.npcs.addAll(npcs);
    }

    public void placeNpc(AbstractNpc npc, HexCoordinate cell) {
        addNpc(npc);
        HexCoordinate claimedCell = claimNearestFreeCell(cell, npc);
        synchronized (npc) {
            npc.component(PositionComponent.class).hexCoordinate = claimedCell;
        }
    }

    public Optional<AbstractNpc> findNpcByName(String name) {
        return npcs.stream().filter(npc -> npc.component(IdentityComponent.class).name.equalsIgnoreCase(name))
                .findFirst();
    }

    public List<RoomPortal> getPortals() {
        return template.getPortals().stream().map(this::toRoomPortal).toList();
    }

    public Optional<RoomPortal> findPortalAt(HexCoordinate cell) {
        return template.getPortals().stream().filter(portal -> portal.cell().equals(cell)).findFirst()
                .map(this::toRoomPortal);
    }

    private RoomPortal toRoomPortal(RoomTemplatePortal portal) {
        RoomInstance target = worldInstance.roomInstanceForTemplate(portal.targetRoomTemplateId())
                .orElseThrow(() -> new IllegalStateException("Portail de " + id + " vers "
                        + portal.targetRoomTemplateId() + ", absente de " + worldInstance.getId()));
        return new RoomPortal(portal.cell(), portal.direction(), this, target, portal.targetCell());
    }

    public Optional<AbstractCharacter> occupantAt(HexCoordinate cell) {
        return Optional.ofNullable(occupants.get(cell));
    }

    public List<AbstractCharacter> occupantsWithin(HexCoordinate center, int radius) {
        return center.withinRadius(radius).stream().map(occupants::get).filter(Objects::nonNull).toList();
    }

    public boolean tryClaimCell(HexCoordinate cell, AbstractCharacter character) {
        return occupants.putIfAbsent(cell, character) == null;
    }

    public void releaseCell(HexCoordinate cell, AbstractCharacter character) {
        if (cell != null) {
            occupants.remove(cell, character);
        }
    }

    private HexCoordinate claimNearestFreeCell(HexCoordinate desired, AbstractCharacter character) {
        if (tryClaimCell(desired, character)) {
            return desired;
        }
        List<HexCoordinate> candidates = desired.withinRadius(3).stream()
                .filter(cell -> !cell.equals(desired) && isInBounds(cell))
                .sorted(Comparator.comparingInt(desired::distanceTo)).toList();
        for (HexCoordinate candidate : candidates) {
            if (tryClaimCell(candidate, character)) {
                return candidate;
            }
        }
        return desired;
    }

    public void broadcast(OutputMessage message, CharacterInstance exclude) {
        for (CharacterInstance character : clients.values()) {
            if (character == exclude || character.findComponent(NetworkComponent.class).isEmpty()) {
                continue;
            }
            character.component(NetworkComponent.class).connection.send(message);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RoomInstance other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "RoomInstance[id=" + id + ", templateId=" + template.getId() + ", worldInstanceId="
                + worldInstance.getId() + "]";
    }
}

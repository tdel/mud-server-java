package fr.idev.mudserver.domain;

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

import fr.idev.mudserver.domain.actor.GameCharacter;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GameNpc;
import fr.idev.mudserver.domain.actor.GamePlayer;
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

    private final Map<UUID, GamePlayer> clients = new ConcurrentHashMap<>();
    private final List<GameMonster> monsters = new CopyOnWriteArrayList<>();
    private final List<GameNpc> npcs = new CopyOnWriteArrayList<>();
    private final Map<HexCoordinate, GameCharacter> occupants = new ConcurrentHashMap<>();

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

    public void join(GamePlayer character) {
        join(character, getSpawnCell());
    }

    public void join(GamePlayer character, HexCoordinate cell) {
        character.setCurrentRoom(this);
        character.setPosition(claimNearestFreeCell(cell, character));
        clients.put(character.getId(), character);
        broadcast(new GamePlayerJoinedRoom(character.getName()), character);
    }

    public void leave(GamePlayer character) {
        clients.remove(character.getId());
        releaseCell(character.getPosition(), character);
        character.setPosition(null);
        broadcast(new GamePlayerLeftRoom(character.getName()), character);
    }

    public void disconnect(GamePlayer character) {
        clients.remove(character.getId());
        releaseCell(character.getPosition(), character);
        character.setPosition(null);
        broadcast(new GamePlayerDisconnected(character.getName()), character);
    }

    public Optional<GameCharacter> findOccupantByName(String name) {
        List<GameCharacter> occupantsByName = new ArrayList<>();
        occupantsByName.addAll(clients.values());
        occupantsByName.addAll(monsters);
        occupantsByName.addAll(npcs);
        return occupantsByName.stream().filter(occupant -> occupant.getName().equalsIgnoreCase(name)).findFirst();
    }

    public List<GamePlayer> characters() {
        return new ArrayList<>(clients.values());
    }

    public List<GameMonster> getMonsters() {
        return List.copyOf(monsters);
    }

    public void addMonster(GameMonster monster) {
        monsters.add(monster);
    }

    public void removeMonster(GameMonster monster) {
        monsters.remove(monster);
        releaseCell(monster.getPosition(), monster);
    }

    public Optional<GameMonster> findMonsterByName(String name) {
        return monsters.stream().filter(monster -> monster.getName().equalsIgnoreCase(name)).findFirst();
    }

    public void setMonsters(List<GameMonster> monsters) {
        this.monsters.clear();
        this.monsters.addAll(monsters);
    }

    public List<MonsterSpawn> getMonsterSpawns() {
        return template.getMonsterSpawns();
    }

    public void placeMonster(GameMonster monster, HexCoordinate cell) {
        addMonster(monster);
        monster.setPosition(claimNearestFreeCell(cell, monster));
    }

    public List<GameNpc> getNpcs() {
        return List.copyOf(npcs);
    }

    public void addNpc(GameNpc npc) {
        npcs.add(npc);
    }

    public void setNpcs(List<GameNpc> npcs) {
        this.npcs.clear();
        this.npcs.addAll(npcs);
    }

    public void placeNpc(GameNpc npc, HexCoordinate cell) {
        addNpc(npc);
        npc.setPosition(claimNearestFreeCell(cell, npc));
    }

    public Optional<GameNpc> findNpcByName(String name) {
        return npcs.stream().filter(npc -> npc.getName().equalsIgnoreCase(name)).findFirst();
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

    public Optional<GameCharacter> occupantAt(HexCoordinate cell) {
        return Optional.ofNullable(occupants.get(cell));
    }

    public List<GameCharacter> occupantsWithin(HexCoordinate center, int radius) {
        return center.withinRadius(radius).stream().map(occupants::get).filter(Objects::nonNull).toList();
    }

    public boolean tryClaimCell(HexCoordinate cell, GameCharacter character) {
        return occupants.putIfAbsent(cell, character) == null;
    }

    public void releaseCell(HexCoordinate cell, GameCharacter character) {
        if (cell != null) {
            occupants.remove(cell, character);
        }
    }

    private HexCoordinate claimNearestFreeCell(HexCoordinate desired, GameCharacter character) {
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

    public void broadcast(OutputMessage message, GamePlayer exclude) {
        for (GamePlayer character : clients.values()) {
            if (character == exclude || character.getConnection() == null) {
                continue;
            }
            character.getConnection().send(message);
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

package fr.idev.mudserver.domain.world;

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
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.GamePlayerSpawnedToZone;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.network.message.ingame.GamePlayerDisconnected;
import fr.idev.mudserver.network.message.ingame.GamePlayerJoinedZone;
import fr.idev.mudserver.network.message.ingame.GamePlayerLeftZone;

public class ZoneInstance {

    public static UUID deterministicId(UUID worldInstanceId, UUID zoneTemplateId) {
        return UUID.nameUUIDFromBytes((worldInstanceId + ":" + zoneTemplateId).getBytes(StandardCharsets.UTF_8));
    }

    private final UUID id;
    private final ZoneTemplate template;
    private final WorldInstance worldInstance;

    private final Map<UUID, CharacterInstance> clients = new ConcurrentHashMap<>();
    private final List<MonsterInstance> monsters = new CopyOnWriteArrayList<>();
    private final List<AbstractNpc> npcs = new CopyOnWriteArrayList<>();
    private final Map<HexCoordinate, AbstractCharacter> occupants = new ConcurrentHashMap<>();

    public ZoneInstance(UUID id, ZoneTemplate template, WorldInstance worldInstance) {
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

    public Boolean isStartingZone() {
        return template.isStartingZone();
    }

    public Map<HexCoordinate, TileType> getTerrain() {
        return template.getTerrain();
    }

    public HexCoordinate getSpawnCell() {
        return template.getSpawnCell();
    }

    public boolean containsCell(HexCoordinate cell) {
        return template.containsCell(cell);
    }

    public boolean isWalkable(HexCoordinate cell) {
        return template.isWalkable(cell);
    }

    public void join(CharacterInstance character) {
        join(character, getSpawnCell());
    }

    public void join(CharacterInstance character, HexCoordinate cell) {
        character.setCurrentZone(this);
        character.setPosition(claimNearestFreeCell(cell, character));
        clients.put(character.getId(), character);
        DomainEventPublisher.publish(new GamePlayerSpawnedToZone(character, this));

        broadcast(new GamePlayerJoinedZone(character.getName()), character);
    }

    public void leave(CharacterInstance character) {
        clients.remove(character.getId());
        releaseCell(character.getPosition(), character);
        character.setPosition(null);
        broadcast(new GamePlayerLeftZone(character.getName()), character);
    }

    public void disconnect(CharacterInstance character) {
        clients.remove(character.getId());
        releaseCell(character.getPosition(), character);
        character.setPosition(null);
        broadcast(new GamePlayerDisconnected(character.getName()), character);
    }

    public Optional<AbstractCharacter> findOccupantByName(String name) {
        List<AbstractCharacter> occupantsByName = new ArrayList<>();
        occupantsByName.addAll(clients.values());
        occupantsByName.addAll(monsters);
        occupantsByName.addAll(npcs);
        return occupantsByName.stream().filter(occupant -> occupant.getName().equalsIgnoreCase(name)).findFirst();
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
        releaseCell(monster.getPosition(), monster);
    }

    public Optional<MonsterInstance> findMonsterByName(String name) {
        return monsters.stream().filter(monster -> monster.getName().equalsIgnoreCase(name)).findFirst();
    }

    public Optional<AbstractCharacter> findAttackableByName(String name, CharacterInstance requester) {
        Optional<MonsterInstance> monster = findMonsterByName(name);
        if (monster.isPresent()) {
            return Optional.of(monster.get());
        }
        return clients.values().stream().filter(client -> !client.getId().equals(requester.getId()))
                .filter(client -> client.getName().equalsIgnoreCase(name)).map(AbstractCharacter.class::cast)
                .findFirst();
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
        monster.setPosition(claimNearestFreeCell(cell, monster));
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
        npc.setPosition(claimNearestFreeCell(cell, npc));
    }

    public Optional<AbstractNpc> findNpcByName(String name) {
        return npcs.stream().filter(npc -> npc.getName().equalsIgnoreCase(name)).findFirst();
    }

    public List<ZonePortal> getPortals() {
        return template.getPortals().stream().map(this::toZonePortal).toList();
    }

    public Optional<ZonePortal> findPortalAt(HexCoordinate cell) {
        return template.getPortals().stream().filter(portal -> portal.cell().equals(cell)).findFirst()
                .map(this::toZonePortal);
    }

    private ZonePortal toZonePortal(ZoneTemplatePortal portal) {
        ZoneInstance target = worldInstance.zoneInstanceForTemplate(portal.targetZoneTemplateId())
                .orElseThrow(() -> new IllegalStateException("Portail de " + id + " vers "
                        + portal.targetZoneTemplateId() + ", absente de " + worldInstance.getId()));
        return new ZonePortal(portal.cell(), portal.direction(), this, target, portal.targetCell());
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

    public boolean isOccupant(AbstractCharacter character) {
        return occupants.containsValue(character);
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
                .filter(cell -> !cell.equals(desired) && isWalkable(cell))
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
        if (!(o instanceof ZoneInstance other)) {
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
        return "ZoneInstance[id=" + id + ", templateId=" + template.getId() + ", worldInstanceId="
                + worldInstance.getId() + "]";
    }
}

package app.domain.world;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.domain.MonsterSpawn;
import app.domain.MonsterSpawnGroup;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.instance.MonsterInstance;
import app.domain.actor.AbstractNpc;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.GamePlayerSpawnedToZone;
import app.domain.map.Position;
import app.network.message.ingame.GamePlayerDisconnected;
import app.network.message.ingame.GamePlayerJoinedZone;
import app.network.message.ingame.GamePlayerLeftZone;

public class ZoneInstance {

    private static final Logger log = LoggerFactory.getLogger(ZoneInstance.class);

    public static UUID deterministicId(UUID worldInstanceId, UUID zoneTemplateId) {
        return UUID.nameUUIDFromBytes((worldInstanceId + ":" + zoneTemplateId).getBytes(StandardCharsets.UTF_8));
    }

    private final UUID id;
    private final ZoneTemplate template;
    private final WorldInstance worldInstance;

    private final Map<UUID, CharacterInstance> clients = new ConcurrentHashMap<>();
    private final List<MonsterInstance> monsters = new CopyOnWriteArrayList<>();
    private final List<AbstractNpc> npcs = new CopyOnWriteArrayList<>();

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

    public CollisionGrid getCollisionGrid() {
        return template.getCollisionGrid();
    }

    public Position getSpawnPosition() {
        return template.getSpawnPosition();
    }

    public boolean containsPosition(Position position) {
        return template.containsPosition(position);
    }

    public boolean isWalkable(Position position) {
        return template.isWalkable(position);
    }

    private static double randomHeading() {
        return ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
    }

    public void join(CharacterInstance character) {
        character.setHeading(randomHeading());
        join(character, getSpawnPosition());
    }

    public void join(CharacterInstance character, Position position) {
        character.setCurrentZone(this);
        character.setPosition(position);
        clients.put(character.getId(), character);
        log.info("zone.joined thread={} zoneId={} character={} position={}", Thread.currentThread().getName(), id,
                character.getId(), position);
        DomainEventPublisher.publish(new GamePlayerSpawnedToZone(character, this));

        character.getKnownList().populateSilently();
        character.broadcast(
                new GamePlayerJoinedZone(character.getId(), character.getName(), position.x(), position.y()),
                character);
    }

    public void leave(CharacterInstance character) {
        clients.remove(character.getId());
        character.getKnownList().clear();
        character.setPosition(null);
        log.info("zone.left thread={} zoneId={} character={}", Thread.currentThread().getName(), id, character.getId());
        character.broadcast(new GamePlayerLeftZone(character.getName()), character);
    }

    public void disconnect(CharacterInstance character) {
        clients.remove(character.getId());
        character.getKnownList().clear();
        character.setPosition(null);
        log.info("zone.disconnected thread={} zoneId={} character={}", Thread.currentThread().getName(), id,
                character.getId());
        character.broadcast(new GamePlayerDisconnected(character.getName()), character);
    }

    public Optional<AbstractCharacter> findOccupantById(UUID id) {
        CharacterInstance client = clients.get(id);
        if (client != null) {
            return Optional.of(client);
        }
        Optional<MonsterInstance> monster = findMonsterById(id);
        if (monster.isPresent()) {
            return Optional.of(monster.get());
        }
        return findNpcById(id).map(AbstractCharacter.class::cast);
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
    }

    public Optional<MonsterInstance> findMonsterById(UUID id) {
        return monsters.stream().filter(monster -> monster.getId().equals(id)).findFirst();
    }

    public Optional<AbstractCharacter> findAttackableById(UUID id, CharacterInstance requester) {
        Optional<MonsterInstance> monster = findMonsterById(id);
        if (monster.isPresent()) {
            return Optional.of(monster.get());
        }
        CharacterInstance client = clients.get(id);
        if (client != null && !client.getId().equals(requester.getId()) && client.getCurrentHealth() > 0) {
            return Optional.of(client);
        }
        return Optional.empty();
    }

    public void setMonsters(List<MonsterInstance> monsters) {
        this.monsters.clear();
        this.monsters.addAll(monsters);
    }

    public List<MonsterSpawn> getMonsterSpawns() {
        return template.getMonsterSpawns();
    }

    public List<MonsterSpawnGroup> getMonsterSpawnGroups() {
        return template.getMonsterSpawnGroups();
    }

    public void placeMonster(MonsterInstance monster, Position position) {
        addMonster(monster);
        monster.setPosition(position);
        monster.setHeading(randomHeading());
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

    public void placeNpc(AbstractNpc npc, Position position) {
        addNpc(npc);
        npc.setPosition(position);
        npc.setHeading(randomHeading());
    }

    public Optional<AbstractNpc> findNpcById(UUID id) {
        return npcs.stream().filter(npc -> npc.getId().equals(id)).findFirst();
    }

    public List<ZonePortal> getPortals() {
        return template.getPortals().stream().map(this::toZonePortal).toList();
    }

    public Optional<ZonePortal> findPortalAt(Position position) {
        return template.getPortals().stream()
                .filter(portal -> portal.position().distanceTo(position) <= portal.triggerRadius()).findFirst()
                .map(this::toZonePortal);
    }

    private ZonePortal toZonePortal(ZoneTemplatePortal portal) {
        ZoneInstance target = worldInstance.zoneInstanceForTemplate(portal.targetZoneTemplateId())
                .orElseThrow(() -> new IllegalStateException("Portail de " + id + " vers "
                        + portal.targetZoneTemplateId() + ", absente de " + worldInstance.getId()));
        return new ZonePortal(portal.position(), portal.direction(), this, target, portal.targetPosition(),
                portal.triggerRadius());
    }

    public boolean isPresent(AbstractCharacter character) {
        return switch (character) {
            case CharacterInstance c -> clients.containsKey(c.getId());
            case MonsterInstance m -> monsters.contains(m);
            case AbstractNpc n -> npcs.contains(n);
            default -> false;
        };
    }

    public List<AbstractCharacter> occupantsWithin(Position center, double radius) {
        List<AbstractCharacter> nearby = new ArrayList<>();
        for (CharacterInstance client : clients.values()) {
            if (client.getPosition() != null && client.getPosition().distanceTo(center) <= radius) {
                nearby.add(client);
            }
        }
        for (MonsterInstance monster : monsters) {
            if (monster.getPosition() != null && monster.getPosition().distanceTo(center) <= radius) {
                nearby.add(monster);
            }
        }
        for (AbstractNpc npc : npcs) {
            if (npc.getPosition() != null && npc.getPosition().distanceTo(center) <= radius) {
                nearby.add(npc);
            }
        }
        return nearby;
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

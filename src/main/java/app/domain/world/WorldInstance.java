package app.domain.world;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.domain.Account;
import app.domain.actor.Attribute;
import app.domain.actor.CharacterClass;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.Gender;
import app.domain.actor.Race;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.NewGamePlayerCreated;
import app.domain.actor.event.PlayerLoadedInWorld;
import app.domain.actor.event.PlayerRemovedFromWorld;
import app.domain.map.Position;

public class WorldInstance {

    private static final Logger log = LoggerFactory.getLogger(WorldInstance.class);

    public static final UUID DEFAULT_ID = UUID.fromString("a8e98a8e-73c1-43dd-b36e-a2f67f00ff48");

    private final UUID id;
    private final UUID worldTemplateId;
    private final Instant createdAt;

    private Map<UUID, MapInstance> mapInstances = Map.of();

    private final Map<UUID, CharacterInstance> players = new ConcurrentHashMap<>();

    public WorldInstance(UUID id, UUID worldTemplateId, Instant createdAt) {
        this.id = id;
        this.worldTemplateId = worldTemplateId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorldTemplateId() {
        return worldTemplateId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setMapInstances(Map<UUID, MapInstance> mapInstances) {
        this.mapInstances = Map.copyOf(mapInstances);
    }

    public Collection<MapInstance> mapInstances() {
        return mapInstances.values();
    }

    public Optional<MapInstance> mapInstanceForTemplate(UUID mapTemplateId) {
        return Optional.ofNullable(mapInstances.get(mapTemplateId));
    }

    public Optional<MapInstance> startingMapInstance() {
        return mapInstances.values().stream().filter(map -> Boolean.TRUE.equals(map.isStartingMap())).findFirst();
    }

    public void loadPlayer(CharacterInstance character) {
        Position savedPosition = character.getPosition();
        if (savedPosition != null) {
            character.getCurrentMap().join(character, savedPosition);
        } else {
            character.getCurrentMap().join(character);
        }
        players.put(character.getId(), character);
        log.info("world.player_loaded thread={} worldId={} character={}", Thread.currentThread().getName(), id,
                character.getId());
        DomainEventPublisher.publish(new PlayerLoadedInWorld(character));
    }

    public void removePlayer(CharacterInstance character) {
        players.remove(character.getId());
        log.info("world.player_removed thread={} worldId={} character={}", Thread.currentThread().getName(), id,
                character.getId());
        DomainEventPublisher.publish(new PlayerRemovedFromWorld(character));
    }

    public Collection<CharacterInstance> onlineCharacters() {
        return List.copyOf(players.values());
    }

    public boolean isCharacterInGame(UUID characterId) {
        return players.containsKey(characterId);
    }

    public CharacterInstance createCharacter(Account account, String name, Gender gender, Race race,
            CharacterClass characterClass) {
        MapInstance startingMap = startingMapInstance()
                .orElseThrow(() -> new IllegalStateException("WorldInstance " + id + " n'a aucune map de départ"));

        Map<Attribute, Integer> scores = new EnumMap<>(characterClass.baseAttributes());
        for (Map.Entry<Attribute, Integer> bonus : race.attributeScoreBonuses().entrySet()) {
            scores.merge(bonus.getKey(), bonus.getValue(), Integer::sum);
        }

        int maxHealth = characterClass.maxHealth(scores.get(Attribute.CONSTITUTION), 1);

        int startingMana = characterClass.maxMana(scores.get(Attribute.MEN), 1);

        CharacterInstance character = new CharacterInstance(UUID.randomUUID(), account, name, startingMap, gender, race,
                characterClass, 1, maxHealth, maxHealth, scores, 0, 0, startingMana, startingMana, Set.of(), List.of(),
                List.of(), Set.of());
        character.setWorldInstance(this);

        DomainEventPublisher.publish(new NewGamePlayerCreated(character));

        return character;
    }

    @Override
    public String toString() {
        return "WorldInstance[id=" + id + ", worldTemplateId=" + worldTemplateId + ", createdAt=" + createdAt + "]";
    }
}

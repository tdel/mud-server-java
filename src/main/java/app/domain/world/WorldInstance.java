package app.domain.world;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import app.game.dice.DiceRoll;
import app.game.dice.DiceRoller;
import app.network.OutputMessage;

public class WorldInstance {

    private static final Logger log = LoggerFactory.getLogger(WorldInstance.class);

    public static final UUID DEFAULT_ID = UUID.fromString("a8e98a8e-73c1-43dd-b36e-a2f67f00ff48");

    private final UUID id;
    private final UUID worldTemplateId;
    private final Instant createdAt;

    private Map<UUID, ZoneInstance> zoneInstances = Map.of();

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

    public void setZoneInstances(Map<UUID, ZoneInstance> zoneInstances) {
        this.zoneInstances = Map.copyOf(zoneInstances);
    }

    public boolean isMaterialized() {
        return !zoneInstances.isEmpty();
    }

    public Collection<ZoneInstance> zoneInstances() {
        return zoneInstances.values();
    }

    public Optional<ZoneInstance> zoneInstanceForTemplate(UUID zoneTemplateId) {
        return Optional.ofNullable(zoneInstances.get(zoneTemplateId));
    }

    public Optional<ZoneInstance> startingZoneInstance() {
        return zoneInstances.values().stream().filter(zone -> Boolean.TRUE.equals(zone.isStartingZone())).findFirst();
    }

    public void loadPlayer(CharacterInstance character) {
        character.getCurrentZone().join(character);
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
        ZoneInstance startingZone = startingZoneInstance()
                .orElseThrow(() -> new IllegalStateException("WorldInstance " + id + " n'a aucune zone de départ"));

        Map<Attribute, Integer> scores = rollAttributeScores();
        for (Map.Entry<Attribute, Integer> bonus : race.attributeScoreBonuses().entrySet()) {
            scores.merge(bonus.getKey(), bonus.getValue(), Integer::sum);
        }

        // 5e niveau 1 : PV max = valeur MAXIMALE du dé de vie de la classe (pas un jet)
        // + modificateur de CON.
        int constitutionModifier = Math.floorDiv(scores.get(Attribute.CONSTITUTION) - 10, 2);
        int maxHealth = Math.max(1, characterClass.hitDie() + constitutionModifier);

        CharacterClass.StartingGold startingGold = characterClass.startingGold();
        int gold = DiceRoller.roll(startingGold.dice()).total() * startingGold.multiplier();

        int startingMana = characterClass.manaGainPerLevel();

        CharacterInstance character = new CharacterInstance(UUID.randomUUID(), account, name, startingZone, gender,
                race, characterClass, 1, maxHealth, maxHealth, scores, 0, gold, 0, startingMana, startingMana);
        character.setWorldInstance(this);

        DomainEventPublisher.publish(new NewGamePlayerCreated(character));

        return character;
    }

    public void broadcast(OutputMessage message, CharacterInstance exclude) {
        for (ZoneInstance zone : this.zoneInstances()) {
            zone.broadcast(message, exclude);
        }
    }

    private Map<Attribute, Integer> rollAttributeScores() {
        Map<Attribute, Integer> scores = new LinkedHashMap<>();
        for (Attribute attribute : Attribute.values()) {
            scores.put(attribute, rollAttributeScore());
        }
        return scores;
    }

    private int rollAttributeScore() {
        // Official 5e method: roll 4d6, drop the lowest single die, sum the rest.
        DiceRoll roll = DiceRoller.roll("4d6");
        int[] dice = roll.rolls().clone();
        Arrays.sort(dice);
        int sum = 0;
        for (int i = 1; i < dice.length; i++) {
            sum += dice[i];
        }
        return sum;
    }

    @Override
    public String toString() {
        return "WorldInstance[id=" + id + ", worldTemplateId=" + worldTemplateId + ", createdAt=" + createdAt + "]";
    }
}

package fr.idev.mudserver.domain.world;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.NewGamePlayerCreated;
import fr.idev.mudserver.game.dice.DiceRoll;
import fr.idev.mudserver.game.dice.DiceRoller;
import fr.idev.mudserver.network.OutputMessage;

public class WorldInstance {

    public static final UUID DEFAULT_ID = UUID.fromString("a8e98a8e-73c1-43dd-b36e-a2f67f00ff48");

    private final UUID id;
    private final UUID worldTemplateId;
    private final Instant createdAt;
    private final UUID partyLeaderAccountId;
    private final Set<UUID> memberAccountIds;

    private Map<UUID, RoomInstance> roomInstances = Map.of();

    private final Map<UUID, CharacterInstance> players = new ConcurrentHashMap<>();

    public WorldInstance(UUID id, UUID worldTemplateId, Instant createdAt, UUID partyLeaderAccountId,
            Set<UUID> memberAccountIds) {
        this.id = id;
        this.worldTemplateId = worldTemplateId;
        this.createdAt = createdAt;
        this.partyLeaderAccountId = partyLeaderAccountId;
        this.memberAccountIds = Set.copyOf(memberAccountIds);
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

    public Optional<UUID> getPartyLeaderAccountId() {
        return Optional.ofNullable(partyLeaderAccountId);
    }

    public Set<UUID> getMemberAccountIds() {
        return memberAccountIds;
    }

    public void setRoomInstances(Map<UUID, RoomInstance> roomInstances) {
        this.roomInstances = Map.copyOf(roomInstances);
    }

    public boolean isMaterialized() {
        return !roomInstances.isEmpty();
    }

    public Collection<RoomInstance> roomInstances() {
        return roomInstances.values();
    }

    public Optional<RoomInstance> roomInstanceForTemplate(UUID roomTemplateId) {
        return Optional.ofNullable(roomInstances.get(roomTemplateId));
    }

    public Optional<RoomInstance> startingRoomInstance() {
        return roomInstances.values().stream().filter(room -> Boolean.TRUE.equals(room.isStartingRoom())).findFirst();
    }

    public void addPlayer(CharacterInstance character) {
        players.put(character.getId(), character);
    }

    public void removePlayer(CharacterInstance character) {
        players.remove(character.getId());
    }

    public Collection<CharacterInstance> onlineCharacters() {
        return List.copyOf(players.values());
    }

    public boolean isCharacterInGame(UUID characterId) {
        return players.containsKey(characterId);
    }

    public CharacterInstance createCharacter(Account account, String name, Gender gender, Race race,
            CharacterClass characterClass) {
        RoomInstance startingRoom = startingRoomInstance()
                .orElseThrow(() -> new IllegalStateException("WorldInstance " + id + " n'a aucune room de départ"));

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

        CharacterInstance character = new CharacterInstance(UUID.randomUUID(), account, name, startingRoom, gender,
                race, characterClass, 1, maxHealth, maxHealth, scores, 0, gold);
        character.setWorldInstance(this);

        DomainEventPublisher.publish(new NewGamePlayerCreated(character));
        character.setCurrentRoom(startingRoom);

        return character;
    }

    public void broadcast(OutputMessage message, CharacterInstance exclude) {
        for (RoomInstance room : this.roomInstances()) {
            room.broadcast(message, exclude);
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
        return "WorldInstance[id=" + id + ", worldTemplateId=" + worldTemplateId + ", createdAt=" + createdAt
                + ", partyLeaderAccountId=" + partyLeaderAccountId + ", members=" + memberAccountIds.size() + "]";
    }
}

package fr.idev.mudserver.domain;

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

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.NewGamePlayerCreated;
import fr.idev.mudserver.game.dice.DiceRoll;
import fr.idev.mudserver.game.dice.DiceRoller;

/**
 * Playthrough concret d'un {@link WorldTemplate}, scopé à une party — chaque
 * party qui lance un même {@code WorldTemplate} obtient sa propre
 * {@code WorldInstance}, avec son propre graphe de {@link RoomInstance}s, ses
 * propres monstres/PNJ/items, invisible aux autres instances. Conflate
 * volontairement métadonnées persistées et conteneur runtime, comme
 * {@link RoomInstance} le fait déjà pour {@link RoomTemplate} — pas de split
 * supplémentaire tant qu'aucun second besoin (éviction sous pression mémoire,
 * par exemple) ne le justifie.
 *
 * <p>
 * {@code roomInstances} est vide tant que {@code WorldInstanceService
 * .materialize} n'a pas tourné (voir {@link #isMaterialized()}) — une
 * {@code WorldInstance} peut exister en DB (créée par une party, puis tout le
 * monde déconnecté) sans être résidente en mémoire, et n'est matérialisée à
 * nouveau qu'à la demande. Keyé par id de {@link RoomTemplate} (pas par id de
 * {@link RoomInstance} lui-même) pour que
 * {@link #roomInstanceForTemplate(UUID)} — la résolution "quelle room du monde"
 * utilisée à la reconnexion d'un personnage — reste une simple consultation de
 * map.
 */
public class WorldInstance {

    /**
     * Id fixe (pas généré à l'exécution) de la {@code WorldInstance} par défaut
     * créée pour ne rien perdre des personnages déjà existants au moment de
     * l'introduction des Worlds — doit rester synchronisé avec le littéral de
     * {@code V8__add_character_world_instance.sql} et avec la migration Java
     * {@code V9__RecomputeDefaultInstanceItemRoomIds}. Référencé par
     * {@code CharacterDao.insert} (repli quand un {@code GamePlayer} construit à la
     * main n'a jamais reçu d'autre instance explicite) et par
     * {@code WorldInstanceService} (chargement/matérialisation au démarrage tant
     * qu'aucun Lobby ne permet encore de choisir un autre monde).
     */
    public static final UUID DEFAULT_ID = UUID.fromString("a8e98a8e-73c1-43dd-b36e-a2f67f00ff48");

    private final UUID id;
    private final UUID worldTemplateId;
    private final Instant createdAt;
    private final UUID partyLeaderAccountId;
    private final Set<UUID> memberAccountIds;

    private Map<UUID, RoomInstance> roomInstances = Map.of();

    /**
     * Joueurs actuellement en jeu dans cette instance — même principe que
     * {@link RoomInstance#clients} à l'échelle de l'instance entière plutôt que
     * d'une seule room. Peuplé par {@code GameWorld.enterWorld}/{@code exitWorld}
     * (le seul point d'entrée/sortie du jeu), jamais directement par un contrôleur.
     * Remplace l'ancien registre centralisé {@code GameWorld} (une seule
     * {@code Map<Connection, GamePlayer>} pour tout le process) : une
     * {@code WorldInstance} est déjà une frontière d'isolation forte (ses propres
     * {@link RoomInstance}s, monstres, PNJ, items), il n'y avait pas de raison de
     * suivre ses joueurs ailleurs qu'ici.
     */
    private final Map<UUID, GamePlayer> players = new ConcurrentHashMap<>();

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

    public void addPlayer(GamePlayer character) {
        players.put(character.getId(), character);
    }

    public void removePlayer(GamePlayer character) {
        players.remove(character.getId());
    }

    public Collection<GamePlayer> onlineCharacters() {
        return List.copyOf(players.values());
    }

    public boolean isCharacterInGame(UUID characterId) {
        return players.containsKey(characterId);
    }

    public GamePlayer createCharacter(Account account, String name, Gender gender, Race race,
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

        // getTemplateId(), pas getId() : current_room_id désigne toujours un id de
        // RoomTemplate (indépendant de l'instance), jamais l'id déterministe de la
        // RoomInstance elle-même — voir la Javadoc de RoomService.
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), name, startingRoom.getTemplateId(),
                gender, race, characterClass, 1, maxHealth, maxHealth, scores, 0, gold);
        character.setWorldInstance(this);

        DomainEventPublisher.publish(new NewGamePlayerCreated(character));
        character.spawnToRoom(startingRoom);

        return character;
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

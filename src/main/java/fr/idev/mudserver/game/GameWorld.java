package fr.idev.mudserver.game;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import fr.idev.mudserver.domain.*;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.NewGamePlayerCreated;
import fr.idev.mudserver.game.dice.DiceRoll;
import fr.idev.mudserver.game.dice.DiceRoller;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.persistence.CharacterDao;

/**
 * Suit tous les joueurs actuellement dans le monde de jeu, pour toute la durée
 * de vie du process, et la session associée à chacun — sur le même principe que
 * {@link AuthWorld} : {@code Connection} n'a donc pas besoin d'exposer
 * d'accesseur de personnage.
 */
@Component
public class GameWorld {

    private static final Logger log = LoggerFactory.getLogger(GameWorld.class);

    private final Map<Connection, GamePlayer> characters = new ConcurrentHashMap<>();

    private final CharacterDao characterDao;
    private final RoomService roomService;
    private final ItemService itemService;

    public GameWorld(CharacterDao characterDao, RoomService roomService, ItemService itemService) {
        this.characterDao = characterDao;
        this.roomService = roomService;
        this.itemService = itemService;
    }

    /**
     * Délègue la résolution de la room de départ à
     * {@link RoomService#spawnCharacter} : un personnage qui vient d'être chargé
     * depuis {@code CharacterDao} n'a que son {@code currentRoomId} persistée
     * ({@code character.getCurrentRoom()} n'est renseigné qu'en effet de bord de
     * {@code GamePlayer#spawnToRoom}) — même principe que
     * {@code itemService.loadInventory(character)} juste au-dessus : le service
     * résout tout à partir du {@code GamePlayer}, {@code GameWorld} ne manipule
     * jamais d'UUID directement.
     */
    public void enterWorld(Connection connection, GamePlayer character) {
        character.setConnection(connection);
        character.getInventory().replaceItems(itemService.loadInventory(character));
        characters.put(connection, character);
        roomService.spawnCharacter(character);
        MDC.put("character", character.getName());
    }

    public void exitWorld(Connection connection) {
        GamePlayer character = characters.remove(connection);
        if (character == null) {
            return;
        }

        RoomInstance room = character.getCurrentRoom();
        characterDao.update(character);
        character.getCurrentRoom().disconnect(character);
        log.info("character.session_ended character={} room={}", character.getName(), room.getName());
        MDC.remove("character");
    }

    public GamePlayer character(Connection connection) {
        return characters.get(connection);
    }

    /**
     * Tous les joueurs actuellement en jeu, consommé par
     * {@code game.actor.RestService} : un repos court/long affecte l'ensemble des
     * joueurs en ligne, pas seulement celui qui l'initie (voir sa Javadoc).
     */
    public Collection<GamePlayer> onlineCharacters() {
        return List.copyOf(characters.values());
    }

    /**
     * Sous-ensemble de {@link #onlineCharacters()} scopé à une
     * {@code WorldInstance} — consommé par {@code game.actor.RestService} : un
     * repos court/long affecte tous les joueurs en ligne de l'instance de
     * l'initiateur, plus le process entier (voir {@code multi-world.md} Phase E).
     */
    public Collection<GamePlayer> onlineCharactersInWorldInstance(UUID worldInstanceId) {
        return characters.values().stream().filter(character -> worldInstanceId.equals(character.getWorldInstanceId()))
                .toList();
    }

    public boolean isCharacterInGame(UUID characterId) {
        return characters.values().stream().anyMatch(character -> character.getId().equals(characterId));
    }

    public boolean isAlreadyConnected(UUID accountId) {
        return characters.values().stream().anyMatch(character -> character.getAccountId().equals(accountId));
    }

    /**
     * Prend la {@link WorldInstance} cible en paramètre (résolue par
     * {@code CharacterSelectionWorld}) plutôt que de passer par
     * {@code roomService.startingRoom()} (scopée à {@link WorldInstance#DEFAULT_ID}
     * uniquement) : ce dernier n'existe que comme délégation de bootstrap pour les
     * tests, pas comme point de résolution générique une fois le Lobby en place.
     */
    public GamePlayer createCharacter(Account account, WorldInstance instance, String name, Gender gender, Race race,
            CharacterClass characterClass) {
        RoomInstance startingRoom = instance.startingRoomInstance().orElseThrow(
                () -> new IllegalStateException("WorldInstance " + instance.getId() + " n'a aucune room de départ"));

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
        character.setWorldInstance(instance);

        DomainEventPublisher.publish(new NewGamePlayerCreated(character));
        character.spawnToRoom(startingRoom);
        log.info("character.created character={} account={} race={} class={}", character.getName(), account.getLogin(),
                race, characterClass);

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

    @EventListener
    void onNewGamePlayerCreated(NewGamePlayerCreated event) {
        characterDao.insert(event.character());
    }
}

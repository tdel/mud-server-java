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
import fr.idev.mudserver.game.actor.ClassService;
import fr.idev.mudserver.game.actor.RaceService;
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
    private final RaceService raceService;
    private final ClassService classService;
    private final DiceRoller diceRoller;

    public GameWorld(CharacterDao characterDao, RoomService roomService, ItemService itemService,
            RaceService raceService, ClassService classService, DiceRoller diceRoller) {
        this.characterDao = characterDao;
        this.roomService = roomService;
        this.itemService = itemService;
        this.raceService = raceService;
        this.classService = classService;
        this.diceRoller = diceRoller;
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

        Room room = character.getCurrentRoom();
        characterDao.update(character);
        character.getCurrentRoom().disconnect(character);
        log.info("character.session_ended character={} room={}", character.getName(), room.getName());
        MDC.remove("character");
    }

    public GamePlayer character(Connection connection) {
        return characters.get(connection);
    }

    public boolean isCharacterInGame(UUID characterId) {
        return characters.values().stream().anyMatch(character -> character.getId().equals(characterId));
    }

    public boolean isAlreadyConnected(UUID accountId) {
        return characters.values().stream().anyMatch(character -> character.getAccountId().equals(accountId));
    }

    public boolean isCharacterNameTaken(UUID accountId, String name) {
        return characterDao.findByAccountIdAndName(accountId, name).isPresent();
    }

    public GamePlayer createCharacter(Account account, String name, Gender gender, Race race,
            CharacterClass characterClass) {
        Optional<Room> startingRoom = roomService.startingRoom();

        Map<Attribute, Integer> scores = rollAttributeScores();
        for (Map.Entry<Attribute, Integer> bonus : raceService.attributeScoreBonuses(race).entrySet()) {
            scores.merge(bonus.getKey(), bonus.getValue(), Integer::sum);
        }

        // 5e niveau 1 : PV max = valeur MAXIMALE du dé de vie de la classe (pas un jet)
        // + modificateur de CON.
        int constitutionModifier = Math.floorDiv(scores.get(Attribute.CONSTITUTION) - 10, 2);
        int maxHealth = Math.max(1, classService.hitDie(characterClass) + constitutionModifier);

        ClassService.StartingGold startingGold = classService.startingGold(characterClass);
        int gold = diceRoller.roll(startingGold.dice()).total() * startingGold.multiplier();

        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), name, startingRoom.get().getId(),
                gender, race, characterClass, classService.primaryAbility(characterClass),
                classService.savingThrowProficiencies(characterClass), classService.skillProficiencies(characterClass),
                classService.weaponProficiencies(characterClass), classService.armorProficiencies(characterClass), 1,
                maxHealth, maxHealth, scores, 0, gold);
        character.setSpeed(raceService.speed(race));

        DomainEventPublisher.publish(new NewGamePlayerCreated(character));
        character.spawnToRoom(startingRoom.get());
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
        DiceRoll roll = diceRoller.roll("4d6");
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

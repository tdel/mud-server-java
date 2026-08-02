package fr.idev.mudserver.controller.authed;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Ability;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Race;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.game.dice.DiceRoll;
import fr.idev.mudserver.game.dice.DiceRoller;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.authed.CharacterAlreadyExists;
import fr.idev.mudserver.network.message.authed.CharacterCreated;
import fr.idev.mudserver.network.message.authed.ChooseRace;
import fr.idev.mudserver.network.message.authed.InvalidRace;
import fr.idev.mudserver.network.message.authed.NoStartingRoom;
import fr.idev.mudserver.network.message.ingame.CharacterStats;
import fr.idev.mudserver.persistence.CharacterDao;
import fr.idev.mudserver.persistence.RoomDao;

@Component
public class CharacterCreate implements ControllerHandler {

    private final CharacterDao characterDao;
    private final RoomDao roomDao;
    private final CharacterList characterListAction;
    private final DiceRoller diceRoller;

    public CharacterCreate(CharacterDao characterDao, RoomDao roomDao, CharacterList characterListAction,
            DiceRoller diceRoller) {
        this.characterDao = characterDao;
        this.roomDao = roomDao;
        this.characterListAction = characterListAction;
        this.diceRoller = diceRoller;
    }

    @Override
    public String name() {
        return "character-create";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.AUTHED);
    }

    @Override
    public void onReceive(Connection session, String argument) {
        String name = argument.trim();

        if (name.isEmpty()) {
            session.send(new Usage("character-create <name>"));
            characterListAction.onReceive(session, "");
            return;
        }

        Account account = session.account();

        if (characterDao.findByAccountIdAndName(account.id(), name).isPresent()) {
            session.send(new CharacterAlreadyExists(name));
            characterListAction.onReceive(session, "");
            return;
        }

        Optional<Room> startingRoom = roomDao.findStartingRoom();
        if (startingRoom.isEmpty()) {
            session.send(new NoStartingRoom());
            characterListAction.onReceive(session, "");
            return;
        }

        promptRace(session, account, startingRoom.get(), name);
    }

    private void promptRace(Connection session, Account account, Room startingRoom, String name) {
        session.requestBlocking(new ChooseRace(), line -> {
            Race race = parseRace(line);

            if (race == null) {
                session.send(new InvalidRace(line.trim()));
                promptRace(session, account, startingRoom, name);
                return;
            }

            createCharacter(session, account, startingRoom, name, race);
        });
    }

    private Race parseRace(String input) {
        String normalized = input.strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return Race.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void createCharacter(Connection session, Account account, Room startingRoom, String name, Race race) {
        Map<Ability, Integer> scores = rollAbilityScores();

        for (Map.Entry<Ability, Integer> bonus : race.abilityScoreBonuses().entrySet()) {
            scores.merge(bonus.getKey(), bonus.getValue(), Integer::sum);
        }

        Character character = new Character(UUID.randomUUID(), account.id(), name, startingRoom.id(), race, 100, 100,
                10, 10, scores.get(Ability.STRENGTH), scores.get(Ability.DEXTERITY), scores.get(Ability.CONSTITUTION),
                scores.get(Ability.INTELLIGENCE), scores.get(Ability.WISDOM), scores.get(Ability.CHARISMA));

        characterDao.insert(character);

        session.send(new CharacterCreated(name));
        session.send(new CharacterStats(character));
        characterListAction.onReceive(session, "");
    }

    private Map<Ability, Integer> rollAbilityScores() {
        Map<Ability, Integer> scores = new LinkedHashMap<>();
        for (Ability ability : Ability.values()) {
            scores.put(ability, rollAbilityScore());
        }
        return scores;
    }

    private int rollAbilityScore() {
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
}

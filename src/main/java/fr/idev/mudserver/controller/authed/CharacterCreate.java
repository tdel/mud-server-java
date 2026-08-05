package fr.idev.mudserver.controller.authed;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Attribute;
import fr.idev.mudserver.domain.GamePlayer;
import fr.idev.mudserver.domain.CharacterClass;
import fr.idev.mudserver.domain.Gender;
import fr.idev.mudserver.domain.Race;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.ClassService;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.RaceService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.authed.CharacterAlreadyExists;
import fr.idev.mudserver.network.message.authed.CharacterCreated;
import fr.idev.mudserver.network.message.authed.ChooseClass;
import fr.idev.mudserver.network.message.authed.ChooseGender;
import fr.idev.mudserver.network.message.authed.ChooseRace;
import fr.idev.mudserver.network.message.authed.InvalidClass;
import fr.idev.mudserver.network.message.authed.InvalidGender;
import fr.idev.mudserver.network.message.authed.InvalidRace;
import fr.idev.mudserver.network.message.authed.NoStartingRoom;
import fr.idev.mudserver.network.message.ingame.GamePlayerStats;

@Component
public class CharacterCreate implements ControllerHandler {

    private final CharacterList characterListAction;
    private final GameWorld gameWorld;
    private final RaceService raceService;
    private final ClassService classService;
    private final AuthWorld authWorld;

    public CharacterCreate(CharacterList characterListAction, GameWorld gameWorld, RaceService raceService,
            ClassService classService, AuthWorld authWorld) {
        this.characterListAction = characterListAction;
        this.gameWorld = gameWorld;
        this.raceService = raceService;
        this.classService = classService;
        this.authWorld = authWorld;
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
    public void onReceive(Connection connection, String argument) {
        String name = argument.trim();

        if (name.isEmpty()) {
            connection.send(new Usage("character-create <name>"));
            characterListAction.onReceive(connection, "");
            return;
        }

        Account account = authWorld.account(connection);

        if (gameWorld.isCharacterNameTaken(account.getId(), name)) {
            connection.send(new CharacterAlreadyExists(name));
            characterListAction.onReceive(connection, "");
            return;
        }

        promptGender(connection, account, name);
    }

    private void promptGender(Connection connection, Account account, String name) {
        connection.requestBlocking(new ChooseGender(), line -> {
            Gender gender = parseGender(line);

            if (gender == null) {
                connection.send(new InvalidGender(line.trim()));
                promptGender(connection, account, name);
                return;
            }

            promptRace(connection, account, name, gender);
        });
    }

    private Gender parseGender(String input) {
        String normalized = input.strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return Gender.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void promptRace(Connection connection, Account account, String name, Gender gender) {
        Map<Race, Map<Attribute, Integer>> bonusesByRace = new LinkedHashMap<>();
        for (Race race : Race.values()) {
            bonusesByRace.put(race, raceService.attributeScoreBonuses(race));
        }

        connection.requestBlocking(new ChooseRace(bonusesByRace), line -> {
            Race race = parseRace(line);

            if (race == null) {
                connection.send(new InvalidRace(line.trim()));
                promptRace(connection, account, name, gender);
                return;
            }

            promptClass(connection, account, name, gender, race);
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

    private void promptClass(Connection connection, Account account, String name, Gender gender, Race race) {
        Map<CharacterClass, Integer> hitDiceByClass = new LinkedHashMap<>();
        for (CharacterClass characterClass : CharacterClass.values()) {
            hitDiceByClass.put(characterClass, classService.hitDie(characterClass));
        }

        connection.requestBlocking(new ChooseClass(hitDiceByClass), line -> {
            CharacterClass characterClass = parseClass(line);

            if (characterClass == null) {
                connection.send(new InvalidClass(line.trim()));
                promptClass(connection, account, name, gender, race);
                return;
            }

            createCharacter(connection, account, name, gender, race, characterClass);
        });
    }

    private CharacterClass parseClass(String input) {
        String normalized = input.strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return CharacterClass.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void createCharacter(Connection connection, Account account, String name, Gender gender, Race race,
            CharacterClass characterClass) {
        GamePlayer character = gameWorld.createCharacter(account, name, gender, race, characterClass);

        connection.send(new CharacterCreated(name));
        connection.send(new GamePlayerStats(character));
        characterListAction.onReceive(connection, "");
    }
}

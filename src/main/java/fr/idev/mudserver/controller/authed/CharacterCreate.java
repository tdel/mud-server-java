package fr.idev.mudserver.controller.authed;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Race;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.authed.CharacterAlreadyExists;
import fr.idev.mudserver.network.message.authed.CharacterCreated;
import fr.idev.mudserver.network.message.authed.ChooseRace;
import fr.idev.mudserver.network.message.authed.InvalidRace;
import fr.idev.mudserver.network.message.authed.NoStartingRoom;
import fr.idev.mudserver.network.message.ingame.CharacterStats;

@Component
public class CharacterCreate implements ControllerHandler {

    private final CharacterList characterListAction;
    private final GameWorld gameWorld;
    private final AuthWorld authWorld;

    public CharacterCreate(CharacterList characterListAction, GameWorld gameWorld, AuthWorld authWorld) {
        this.characterListAction = characterListAction;
        this.gameWorld = gameWorld;
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

        promptRace(connection, account, name);
    }

    private void promptRace(Connection connection, Account account, String name) {
        connection.requestBlocking(new ChooseRace(), line -> {
            Race race = parseRace(line);

            if (race == null) {
                connection.send(new InvalidRace(line.trim()));
                promptRace(connection, account, name);
                return;
            }

            createCharacter(connection, account, name, race);
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

    private void createCharacter(Connection connection, Account account, String name, Race race) {
        Character character = gameWorld.createCharacter(account, name, race);

        connection.send(new CharacterCreated(name));
        connection.send(new CharacterStats(character));
        characterListAction.onReceive(connection, "");
    }
}

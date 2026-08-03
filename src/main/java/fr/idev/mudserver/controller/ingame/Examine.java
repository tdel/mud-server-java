package fr.idev.mudserver.controller.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.RoomService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.CharacterNotFound;
import fr.idev.mudserver.network.message.ingame.CharacterStats;

@Component
public class Examine implements ControllerHandler {

    private final GameWorld gameWorld;
    private final RoomService roomService;

    public Examine(GameWorld gameWorld, RoomService roomService) {
        this.gameWorld = gameWorld;
        this.roomService = roomService;
    }

    @Override
    public String name() {
        return "examine";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        Character character = gameWorld.character(connection);
        String name = argument.trim();

        if (name.isEmpty()) {
            connection.send(new Usage("examine <name>"));
            return;
        }

        Character target = character.getCurrentRoom().findCharacterByName(name);

        if (target == null) {
            connection.send(new CharacterNotFound(name));
            return;
        }

        connection.send(new CharacterStats(target));
    }
}

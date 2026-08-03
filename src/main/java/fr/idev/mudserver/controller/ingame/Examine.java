package fr.idev.mudserver.controller.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.Client;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.CharacterNotFound;
import fr.idev.mudserver.network.message.ingame.CharacterStats;

@Component
public class Examine implements ControllerHandler {

    private final GameWorld gameWorld;

    public Examine(GameWorld gameWorld) {
        this.gameWorld = gameWorld;
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
        Client client = gameWorld.client(connection);
        String name = argument.trim();

        if (name.isEmpty()) {
            client.send(new Usage("examine <name>"));
            return;
        }

        Character target = gameWorld.roomInstance(client.character().getCurrentRoomId()).findCharacterByName(name);

        if (target == null) {
            client.send(new CharacterNotFound(name));
            return;
        }

        client.send(new CharacterStats(target));
    }
}

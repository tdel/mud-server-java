package fr.idev.mudserver.controller.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.Chat;
import fr.idev.mudserver.network.message.ingame.SayNothing;
import fr.idev.mudserver.network.message.ingame.YouSaid;

@Component
public class Say implements ControllerHandler {

    private final GameWorld gameWorld;
    private final WorldInstanceService worldInstanceService;

    public Say(GameWorld gameWorld, WorldInstanceService worldInstanceService) {
        this.gameWorld = gameWorld;
        this.worldInstanceService = worldInstanceService;
    }

    @Override
    public String name() {
        return "say";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        GamePlayer character = gameWorld.character(connection);
        String message = argument.trim();

        if (message.isEmpty()) {
            connection.send(new SayNothing());
            return;
        }

        worldInstanceService.broadcastToInstance(character.getWorldInstance(), new Chat(character.getName(), message),
                character);

        connection.send(new YouSaid(message));
    }
}

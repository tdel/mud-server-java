package fr.idev.mudserver.controller.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.PlayerInstance;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.Chat;
import fr.idev.mudserver.network.message.ingame.SayNothing;
import fr.idev.mudserver.network.message.ingame.YouSaid;

@Component
public class Say implements ControllerHandler {

    private final GameWorld gameWorld;

    public Say(GameWorld gameWorld) {
        this.gameWorld = gameWorld;
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
    public void onReceive(Connection session, String argument) {
        PlayerInstance player = gameWorld.player(session);
        String message = argument.trim();

        if (message.isEmpty()) {
            player.send(new SayNothing());
            return;
        }

        Character character = player.character();

        gameWorld.roomInstance(character.getCurrentRoomId()).broadcast(new Chat(character.getName(), message), player);

        player.send(new YouSaid(message));
    }
}

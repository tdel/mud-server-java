package fr.idev.mudserver.network.action.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.PlayerInstance;
import fr.idev.mudserver.network.ActionHandler;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.Session;
import fr.idev.mudserver.network.message.ingame.Chat;
import fr.idev.mudserver.network.message.ingame.SayNothing;
import fr.idev.mudserver.network.message.ingame.YouSaid;

@Component
public class Say implements ActionHandler {

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
    public void onReceive(Session session, String argument) {
        PlayerInstance player = session.player();
        String message = argument.trim();

        if (message.isEmpty()) {
            player.send(new SayNothing());
            return;
        }

        Character character = player.character();

        gameWorld.roomInstance(character.currentRoomId()).broadcast(new Chat(character.name(), message), player);

        player.send(new YouSaid(message));
    }
}

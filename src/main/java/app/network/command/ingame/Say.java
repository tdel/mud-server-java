package app.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.domain.actor.instance.CharacterInstance;
import app.game.WorldInstanceService;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.Chat;
import app.network.message.ingame.SayNothing;
import app.network.message.ingame.YouSaid;

@Component
public class Say implements CommandHandler {

    private final WorldInstanceService worldInstanceService;

    public Say(WorldInstanceService worldInstanceService) {
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
        CharacterInstance character = connection.character();
        String message = argument.trim();

        if (message.isEmpty()) {
            connection.send(new SayNothing());
            return;
        }

        character.getWorldInstance().broadcast(new Chat(character.getName(), message), character);
        connection.send(new YouSaid(message));
    }
}

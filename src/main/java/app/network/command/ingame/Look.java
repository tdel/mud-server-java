package app.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.domain.actor.instance.CharacterInstance;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.ViewAround;

@Component
public class Look implements CommandHandler {

    @Override
    public String name() {
        return "look";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        connection.send(new ViewAround(character));
    }
}

package app.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.GamePlayerStats;

@Component
public class Stats implements CommandHandler {

    @Override
    public String name() {
        return "stats";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        connection.send(new GamePlayerStats(connection.character()));
    }
}

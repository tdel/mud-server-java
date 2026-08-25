package app.network.command.connected;

import java.util.Set;

import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.connected.Goodbye;

@Component
public class Quit implements CommandHandler {

    @Override
    public String name() {
        return "quit";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.CONNECTED);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        connection.send(new Goodbye());
        connection.close();
    }
}

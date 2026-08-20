package fr.idev.mudserver.network.command.connected;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.network.CommandHandler;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.connected.Goodbye;

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

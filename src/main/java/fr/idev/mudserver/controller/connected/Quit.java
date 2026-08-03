package fr.idev.mudserver.controller.connected;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.connected.Goodbye;

@Component
public class Quit implements ControllerHandler {

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

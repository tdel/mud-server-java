package fr.idev.mudserver.controller;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;

@Component
public class Help implements ControllerHandler {

    private final List<ControllerHandler> actions;

    public Help(List<ControllerHandler> actions) {
        this.actions = actions;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.CONNECTED, ConnectionState.LOBBY, ConnectionState.CHARSELECT,
                ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        List<String> commands = actions.stream().filter(a -> a.states().contains(connection.state()))
                .map(ControllerHandler::name).sorted().toList();
        connection.send(new fr.idev.mudserver.network.message.Help(commands));
    }
}

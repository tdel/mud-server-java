package fr.idev.mudserver.network.command;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.network.CommandHandler;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;

@Component
public class Help implements CommandHandler {

    private final List<CommandHandler> actions;

    public Help(List<CommandHandler> actions) {
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
                .map(CommandHandler::name).sorted().toList();
        connection.send(new fr.idev.mudserver.network.message.Help(commands));
    }
}

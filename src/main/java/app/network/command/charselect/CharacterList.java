package app.network.command.charselect;

import java.util.Set;

import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;

@Component
public class CharacterList implements CommandHandler {

    private final CharSelectStatus charSelectStatus;

    public CharacterList(CharSelectStatus charSelectStatus) {
        this.charSelectStatus = charSelectStatus;
    }

    @Override
    public String name() {
        return "character-list";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.CHARSELECT);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        charSelectStatus.show(connection, connection.account());
    }
}

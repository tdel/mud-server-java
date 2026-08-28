package app.network;

import java.util.Set;

public interface CommandHandler {

    String name();

    Set<ConnectionState> states();

    void onReceive(Connection connection, String argument);

    default boolean requiresAlive() {
        return false;
    }
}

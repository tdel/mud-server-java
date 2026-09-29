package app.network;

import java.util.Set;

public interface CommandHandler {

    String name();

    Set<ConnectionState> states();

    void onReceive(Connection connection, String argument);

    default boolean requiresAlive() {
        return false;
    }

    default boolean requiresNotCasting() {
        return false;
    }

    // Refusée entre la fin d'un Scroll of Escape et la téléportation (cf.
    // EscapeEngine) : par défaut toute action refusée pendant une incantation.
    default boolean blockedWhileTeleporting() {
        return requiresNotCasting();
    }
}

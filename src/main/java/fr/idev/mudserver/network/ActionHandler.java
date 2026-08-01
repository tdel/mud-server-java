package fr.idev.mudserver.network;

import java.util.Set;

public interface ActionHandler {

    /** Le mot de commande qu'un joueur tape pour déclencher cette action (ex. "look"). */
    String name();

    Set<ConnectionState> states();

    void onReceive(Session session, String argument);
}

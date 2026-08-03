package fr.idev.mudserver.controller;

import java.util.Set;

import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;

public interface ControllerHandler {

    /**
     * Le mot de commande qu'un joueur tape pour déclencher cette action (ex.
     * "look").
     */
    String name();

    Set<ConnectionState> states();

    void onReceive(Connection connection, String argument);
}

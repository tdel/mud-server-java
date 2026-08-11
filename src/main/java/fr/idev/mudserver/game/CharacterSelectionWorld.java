package fr.idev.mudserver.game;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;

/**
 * Suit tous les clients ayant rejoint une {@link WorldInstance} depuis le Lobby
 * mais pas encore en train de jouer dedans — même principe que
 * {@link AuthWorld} / {@link GameWorld} : {@code Connection} n'a donc pas
 * besoin d'exposer d'accesseur de {@code WorldInstance}. La connexion reste par
 * ailleurs enregistrée dans {@link AuthWorld} tout du long (jamais retirée par
 * cette classe) : {@code CharacterCreate}/{@code CharacterSelect}/
 * {@code CharacterDelete} ont donc simultanément accès au compte via
 * {@link AuthWorld#account} et à l'instance courante via
 * {@link #worldInstance}. Seul {@code AuthWorld#moveToGameWorld} (CHARSELECT ->
 * INGAME) retire la connexion d'{@code AuthWorld} ; {@code Logout} la
 * ré-enregistre explicitement au retour en jeu vers CHARSELECT (voir sa
 * Javadoc).
 */
@Component
public class CharacterSelectionWorld {

    private final Map<Connection, WorldInstance> connections = new ConcurrentHashMap<>();

    public void enterWorld(Connection connection, WorldInstance instance) {
        connections.put(connection, instance);
        connection.setState(ConnectionState.CHARSELECT);
    }

    public void exitWorld(Connection connection) {
        connections.remove(connection);
        connection.setState(ConnectionState.LOBBY);
    }

    public WorldInstance worldInstance(Connection connection) {
        return connections.get(connection);
    }
}

package fr.idev.mudserver.game;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.Connection;

/**
 * Suit tous les clients authentifiés mais pas encore en train de jouer, et le
 * compte associé à chacun — remplace le {@code SplObjectStorage} PHP par un
 * {@link ConcurrentHashMap}, dont l'itérateur (voir
 * {@link #isAlreadyConnected}) tolère un ajout/retrait concurrent sans copie
 * défensive préalable, contrairement à l'original. Le compte vit ici plutôt que
 * sur la session elle-même : {@code Connection} n'a donc pas besoin d'exposer
 * d'accesseur de compte.
 */
@Component
public class AuthWorld {

    private final Map<Connection, Account> connectedSessions = new ConcurrentHashMap<>();

    public void enterWorld(Connection session, Account account) {
        connectedSessions.put(session, account);
        session.setState(ConnectionState.AUTHED);
    }

    public void exitWorld(Connection session) {
        connectedSessions.remove(session);
        session.setState(ConnectionState.CONNECTED);
    }

    /**
     * Détache la session du suivi "connecté mais pas en jeu" sans repasser par
     * l'état {@code CONNECTED} — l'appelant (voir {@code CharacterSelect}) enchaîne
     * directement sur l'état {@code INGAME}. Ne pas remplacer par
     * {@code exitWorld()} + {@code setState()} : {@code exitWorld()} vide aussi le
     * compte attaché à la session.
     */
    public void moveToGameWorld(Connection session) {
        connectedSessions.remove(session);
    }

    public Account account(Connection session) {
        return connectedSessions.get(session);
    }

    public boolean isAlreadyConnected(UUID accountId) {
        return connectedSessions.values().stream().anyMatch(account -> account.getId().equals(accountId));
    }
}

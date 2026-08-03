package fr.idev.mudserver.game;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.persistence.AccountDao;
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

    private final GameWorld gameWorld;
    private final AccountDao accountDao;

    public AuthWorld(GameWorld gameWorld, AccountDao accountDao) {
        this.gameWorld = gameWorld;
        this.accountDao = accountDao;
    }

    public void enterWorld(Connection session, Account account) {
        connectedSessions.put(session, account);
        session.setState(ConnectionState.AUTHED);
    }

    public void exitWorld(Connection session) {
        connectedSessions.remove(session);
        session.setState(ConnectionState.CONNECTED);
    }

    public void moveToGameWorld(Connection session, Character character) {
        Account account = connectedSessions.remove(session);

        accountDao.updateCurrentCharacter(account.getId(), character.getId());

        Client client = new Client(session, character);
        session.setState(ConnectionState.INGAME);
        gameWorld.enterWorld(session, client);
    }

    public Account account(Connection session) {
        return connectedSessions.get(session);
    }

    public boolean isAlreadyConnected(UUID accountId) {
        return connectedSessions.values().stream().anyMatch(account -> account.getId().equals(accountId));
    }
}

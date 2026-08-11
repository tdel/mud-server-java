package fr.idev.mudserver.game;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.persistence.AccountDao;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.OutputMessage;

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

    private final Map<Connection, Account> connections = new ConcurrentHashMap<>();

    private final GameWorld gameWorld;
    private final AccountDao accountDao;

    public AuthWorld(GameWorld gameWorld, AccountDao accountDao) {
        this.gameWorld = gameWorld;
        this.accountDao = accountDao;
    }

    public void enterWorld(Connection connection, Account account) {
        connections.put(connection, account);
        connection.setState(ConnectionState.LOBBY);
        MDC.put("account", account.getLogin());
    }

    public void exitWorld(Connection connection) {
        connections.remove(connection);
        connection.setState(ConnectionState.CONNECTED);
        MDC.remove("account");
    }

    public void moveToGameWorld(Connection connection, GamePlayer character) {
        Account account = connections.remove(connection);

        accountDao.updateCurrentCharacter(account.getId(), character.getId());

        connection.setState(ConnectionState.INGAME);
        gameWorld.enterWorld(connection, character);
    }

    public Account account(Connection connection) {
        return connections.get(connection);
    }

    /**
     * Diffuse {@code message} à toute connexion actuellement en {@code LOBBY} —
     * exclut volontairement {@code CHARSELECT}, bien que {@link #connections} suive
     * les deux états (voir la Javadoc de {@link #findConnectionByLogin}).
     * Symétrique de {@code WorldInstanceService#broadcastToInstance} côté lobby.
     */
    public void broadcastToLobby(OutputMessage message, Connection exclude) {
        for (Connection connection : connections.keySet()) {
            if (connection == exclude || connection.state() != ConnectionState.LOBBY)
                continue;
            connection.send(message);
        }
    }

    public boolean isAlreadyConnected(UUID accountId) {
        return connections.values().stream().anyMatch(account -> account.getId().equals(accountId));
    }

    /**
     * Résout un login vers sa connexion vivante actuelle (LOBBY ou CHARSELECT, voir
     * la Javadoc de classe) — utilisé par {@code controller.lobby.PartyInvite} pour
     * vérifier qu'une cible est bien joignable avant de lui envoyer une invitation.
     */
    public Optional<Connection> findConnectionByLogin(String login) {
        return connections.entrySet().stream().filter(entry -> entry.getValue().getLogin().equalsIgnoreCase(login))
                .map(Map.Entry::getKey).findFirst();
    }

    /**
     * Même principe que {@link #findConnectionByLogin}, mais par id de compte —
     * c'est la seule donnée que retient {@code domain.PartyMember}, utilisé par
     * {@code controller.lobby.WorldEnter} pour résoudre chaque membre d'une party
     * vers sa connexion courante au moment du lancement.
     */
    public Optional<Connection> findConnectionByAccountId(UUID accountId) {
        return connections.entrySet().stream().filter(entry -> entry.getValue().getId().equals(accountId))
                .map(Map.Entry::getKey).findFirst();
    }
}

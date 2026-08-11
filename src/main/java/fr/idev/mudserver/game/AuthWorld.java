package fr.idev.mudserver.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.persistence.AccountDao;

/**
 * Suit tous les comptes authentifiés — de l'entrée en {@code LOBBY} jusqu'au
 * logout ou à la déconnexion — et porte les règles métier d'inscription et
 * d'authentification (recherche de compte, validation et vérification de mot de
 * passe, création de compte). Un {@link Account} reste enregistré ici tout du
 * long, quel que soit l'état de la connexion ({@code LOBBY}, {@code CHARSELECT}
 * ou {@code INGAME}) — seul {@link #exitWorld} le retire, jamais un passage par
 * {@code CHARSELECT}/{@code INGAME}.
 *
 * <p>
 * Le suivi de la {@code WorldInstance} choisie ({@code CHARSELECT}) et du
 * {@code GamePlayer} vivant en jeu ({@code INGAME}) ne vit plus ici : ces deux
 * responsabilités ont été déplacées vers {@code WorldInstanceService}, qui
 * porte désormais son propre registre {@code Connection -> WorldInstance} et le
 * cycle de vie du personnage en jeu, symétriquement à ce que cette classe fait
 * pour le compte.
 *
 * <p>
 * Remplace le {@code SplObjectStorage} PHP par un ensemble issu de
 * {@link ConcurrentHashMap#newKeySet()}, dont l'itérateur (voir
 * {@link #isAlreadyConnected}) tolère un ajout/retrait concurrent sans copie
 * défensive préalable, contrairement à l'original. Le compte associé à chaque
 * connexion n'est plus dupliqué ici : il vit directement sur la
 * {@link Connection} elle-même (voir {@code Connection.account()}), ce registre
 * ne sert plus qu'à énumérer les connexions authentifiées et à résoudre une
 * connexion à partir d'un login/id de compte.
 */
@Component
public class AuthWorld {

    private static final Logger log = LoggerFactory.getLogger(AuthWorld.class);

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();

    private final AccountDao accountDao;
    private final PasswordEncoder passwordEncoder;

    public AuthWorld(AccountDao accountDao, PasswordEncoder passwordEncoder) {
        this.accountDao = accountDao;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Recherche un compte persisté par login — à ne pas confondre avec
     * {@link #findConnectionByLogin}, qui résout une connexion vivante en mémoire,
     * pas un compte en base.
     */
    public Optional<Account> findOneAccountByLogin(String login) {
        return accountDao.findByLogin(login);
    }

    public boolean checkPassword(Account account, String rawPassword) {
        return passwordEncoder.matches(rawPassword, account.getPassword());
    }

    public List<String> validatePassword(String password) {
        List<String> reasons = new ArrayList<>();
        if (password.isEmpty()) {
            reasons.add("This value should not be blank.");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            reasons.add("This value is too short. It should have " + MIN_PASSWORD_LENGTH + " characters or more.");
        } else if (password.length() > MAX_PASSWORD_LENGTH) {
            reasons.add("This value is too long. It should have " + MAX_PASSWORD_LENGTH + " characters or less.");
        }
        return reasons;
    }

    /**
     * Instancie, persiste et fait entrer un nouveau compte dans le lobby.
     *
     * @throws DuplicateKeyException
     *             si {@code login} est déjà pris — l'appelant a en général déjà
     *             vérifié via {@link #findOneAccountByLogin}, mais cette
     *             vérification ne suffit pas à exclure la course avec un autre
     *             enregistrement concurrent ; seule la contrainte unique en base
     *             fait foi.
     */
    public Account registerAccount(Connection connection, String login, String password) {
        Account account = new Account(UUID.randomUUID(), login, passwordEncoder.encode(password), null);
        try {
            accountDao.insert(account);
        } catch (DuplicateKeyException e) {
            log.warn("account.register_conflict account={}", login);
            throw e;
        }

        enterWorld(connection, account);
        log.info("account.registered account={}", login);

        return account;
    }

    public void enterWorld(Connection connection, Account account) {
        connection.setAccount(account);
        connections.add(connection);
        connection.setState(ConnectionState.LOBBY);
        MDC.put("account", account.getLogin());
    }

    public void exitWorld(Connection connection) {
        connections.remove(connection);
        connection.setAccount(null);
        connection.setState(ConnectionState.CONNECTED);
        MDC.remove("account");
    }

    /**
     * Diffuse {@code message} à toute connexion actuellement en {@code LOBBY} —
     * exclut volontairement {@code CHARSELECT}, bien que {@link #accounts} suive
     * aussi cet état (voir la Javadoc de {@link #findConnectionByLogin}).
     * Symétrique de {@code WorldInstanceService#broadcastToInstance} côté lobby.
     */
    public void broadcastToLobby(OutputMessage message, Connection exclude) {
        for (Connection connection : connections) {
            if (connection == exclude || connection.state() != ConnectionState.LOBBY)
                continue;
            connection.send(message);
        }
    }

    public boolean isAlreadyConnected(UUID accountId) {
        return connections.stream().anyMatch(connection -> connection.account().getId().equals(accountId));
    }

    /**
     * Résout un login vers sa connexion vivante actuelle (LOBBY, CHARSELECT ou
     * désormais aussi INGAME, voir la Javadoc de classe) — utilisé par
     * {@code controller.lobby.PartyInvite} pour vérifier qu'une cible est bien
     * joignable avant de lui envoyer une invitation.
     */
    public Optional<Connection> findConnectionByLogin(String login) {
        return connections.stream().filter(connection -> connection.account().getLogin().equalsIgnoreCase(login))
                .findFirst();
    }

    /**
     * Même principe que {@link #findConnectionByLogin}, mais par id de compte —
     * c'est la seule donnée que retient {@code domain.PartyMember}, utilisé par
     * {@code controller.lobby.WorldEnter} pour résoudre chaque membre d'une party
     * vers sa connexion courante au moment du lancement.
     */
    public Optional<Connection> findConnectionByAccountId(UUID accountId) {
        return connections.stream().filter(connection -> connection.account().getId().equals(accountId)).findFirst();
    }
}

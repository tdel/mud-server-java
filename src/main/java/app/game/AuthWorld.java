package app.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import app.network.message.connected.AccountCreated;
import app.network.message.connected.WelcomeBack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import app.domain.Account;
import app.network.ConnectionState;
import app.network.Connection;
import app.network.command.charselect.CharSelectStatus;
import app.persistence.AccountDao;

@Component
public class AuthWorld {

    private static final Logger log = LoggerFactory.getLogger(AuthWorld.class);

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private final Map<UUID, Connection> connectionsByAccount = new ConcurrentHashMap<>();

    private final AccountDao accountDao;
    private final PasswordEncoder passwordEncoder;
    private final WorldInstanceService worldInstanceService;
    private final CharSelectStatus charSelectStatus;

    public AuthWorld(AccountDao accountDao, PasswordEncoder passwordEncoder, WorldInstanceService worldInstanceService,
            CharSelectStatus charSelectStatus) {
        this.accountDao = accountDao;
        this.passwordEncoder = passwordEncoder;
        this.worldInstanceService = worldInstanceService;
        this.charSelectStatus = charSelectStatus;
    }

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

    public Account registerAccount(Connection connection, String login, String password) {
        Account account = new Account(UUID.randomUUID(), login, passwordEncoder.encode(password));
        try {
            accountDao.insert(account);
        } catch (DuplicateKeyException e) {
            log.warn("account.register_conflict account={}", login);
            throw e;
        }

        connection.send(new AccountCreated(account.getLogin()));

        // Un compte tout juste créé a un UUID neuf : ne peut jamais déjà être dans
        // connectionsByAccount.
        tryEnterWorld(connection, account);
        log.info("account.registered account={}", login);

        return account;
    }

    // putIfAbsent rend l'attribution connexion<->compte atomique : ferme le TOCTOU
    // qu'un scan
    // (isAlreadyConnected) suivi d'un add séparé laissait ouvert entre deux logins
    // simultanés.
    public boolean tryEnterWorld(Connection connection, Account account) {
        Connection previous = connectionsByAccount.putIfAbsent(account.getId(), connection);
        if (previous != null) {
            log.warn("auth.duplicate_connection_detected thread={} accountId={}", Thread.currentThread().getName(),
                    account.getId());
            return false;
        }

        connection.setAccount(account);
        connection.attachWorldInstance(worldInstanceService.getDefaultInstance());
        MDC.put("account", account.getLogin());
        log.info("auth.entered_world thread={} account={}", Thread.currentThread().getName(), account.getLogin());

        connection.send(new WelcomeBack(account.getLogin()));
        charSelectStatus.show(connection, account);
        return true;
    }

    public void exitWorld(Connection connection) {
        Account account = connection.account();
        if (account != null) {
            connectionsByAccount.remove(account.getId(), connection);
        }
        String login = account != null ? account.getLogin() : null;
        connection.setAccount(null);
        connection.setState(ConnectionState.CONNECTED);
        log.info("auth.exited_world thread={} account={}", Thread.currentThread().getName(), login);
        MDC.remove("account");
    }
}

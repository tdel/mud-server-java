package fr.idev.mudserver.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.idev.mudserver.network.message.connected.AccountCreated;
import fr.idev.mudserver.network.message.connected.WelcomeBack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.command.charselect.CharSelectStatus;
import fr.idev.mudserver.persistence.AccountDao;

@Component
public class AuthWorld {

    private static final Logger log = LoggerFactory.getLogger(AuthWorld.class);

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();

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

        enterWorld(connection, account);
        log.info("account.registered account={}", login);

        return account;
    }

    public void enterWorld(Connection connection, Account account) {
        connection.setAccount(account);
        connections.add(connection);
        connection.attachWorldInstance(worldInstanceService.getDefaultInstance());
        MDC.put("account", account.getLogin());

        connection.send(new WelcomeBack(account.getLogin()));
        charSelectStatus.show(connection, account);
    }

    public void exitWorld(Connection connection) {
        connections.remove(connection);
        connection.setAccount(null);
        connection.setState(ConnectionState.CONNECTED);
        MDC.remove("account");
    }

    public boolean isAlreadyConnected(UUID accountId) {
        return connections.stream().anyMatch(connection -> connection.account().getId().equals(accountId));
    }
}

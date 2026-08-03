package fr.idev.mudserver.controller.connected;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.controller.authed.CharacterList;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.connected.AccountCreated;
import fr.idev.mudserver.network.message.connected.ConfirmPassword;
import fr.idev.mudserver.network.message.connected.InvalidPassword;
import fr.idev.mudserver.network.message.connected.LoginAlreadyTaken;
import fr.idev.mudserver.network.message.connected.PasswordMismatch;
import fr.idev.mudserver.network.message.connected.RequestPassword;
import fr.idev.mudserver.persistence.AccountDao;

@Component
public class Register implements ControllerHandler {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private final AccountDao accountDao;
    private final AuthWorld authWorld;
    private final PasswordEncoder passwordEncoder;
    private final CharacterList characterListAction;

    public Register(AccountDao accountDao, AuthWorld authWorld, PasswordEncoder passwordEncoder,
            CharacterList characterListAction) {
        this.accountDao = accountDao;
        this.authWorld = authWorld;
        this.passwordEncoder = passwordEncoder;
        this.characterListAction = characterListAction;
    }

    @Override
    public String name() {
        return "register";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.CONNECTED);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        String login = argument.trim();
        if (login.isEmpty()) {
            connection.send(new Usage("register <name>"));
            return;
        }

        if (accountDao.findByLogin(login).isPresent()) {
            connection.send(new LoginAlreadyTaken(login));
            return;
        }

        connection.requestBlocking(new RequestPassword(), password -> onPasswordEntered(connection, login, password));
    }

    private void onPasswordEntered(Connection connection, String login, String password) {
        List<String> reasons = validatePassword(password);
        if (!reasons.isEmpty()) {
            connection.send(new InvalidPassword(reasons));
            return;
        }

        connection.requestBlocking(new ConfirmPassword(),
                confirmation -> onPasswordConfirmed(connection, login, password, confirmation));
    }

    private void onPasswordConfirmed(Connection connection, String login, String password, String confirmation) {
        if (!password.equals(confirmation)) {
            connection.send(new PasswordMismatch());
            return;
        }

        Account account = new Account(UUID.randomUUID(), login, passwordEncoder.encode(password), null);
        try {
            accountDao.insert(account);
        } catch (DuplicateKeyException e) {
            connection.send(new LoginAlreadyTaken(login));
            return;
        }

        authWorld.enterWorld(connection, account);

        connection.send(new AccountCreated(login));
        characterListAction.onReceive(connection, "");
    }

    private List<String> validatePassword(String password) {
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
}

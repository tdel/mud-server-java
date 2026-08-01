package fr.idev.mudserver.network.action.connected;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.network.ActionHandler;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.Session;
import fr.idev.mudserver.network.action.authed.CharacterList;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.connected.AccountCreated;
import fr.idev.mudserver.network.message.connected.InvalidPassword;
import fr.idev.mudserver.network.message.connected.LoginAlreadyTaken;
import fr.idev.mudserver.network.message.connected.PasswordMismatch;
import fr.idev.mudserver.persistence.AccountDao;

@Component
public class Register implements ActionHandler {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private final AccountDao accountDao;
    private final AuthWorld authWorld;
    private final PasswordEncoder passwordEncoder;
    private final CharacterList characterListAction;

    public Register(AccountDao accountDao, AuthWorld authWorld, PasswordEncoder passwordEncoder, CharacterList characterListAction) {
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
    public void onReceive(Session session, String argument) {
        String login = argument.trim();
        if (login.isEmpty()) {
            session.send(new Usage("register <name>"));
            return;
        }

        if (accountDao.findByLogin(login).isPresent()) {
            session.send(new LoginAlreadyTaken(login));
            return;
        }

        session.promptMasked("Password: ", password -> onPasswordEntered(session, login, password));
    }

    private void onPasswordEntered(Session session, String login, String password) {
        List<String> reasons = validatePassword(password);
        if (!reasons.isEmpty()) {
            session.send(new InvalidPassword(reasons));
            return;
        }

        session.promptMasked("Confirm password: ", confirmation -> onPasswordConfirmed(session, login, password, confirmation));
    }

    private void onPasswordConfirmed(Session session, String login, String password, String confirmation) {
        if (!password.equals(confirmation)) {
            session.send(new PasswordMismatch());
            return;
        }

        Account account = new Account(UUID.randomUUID(), login, passwordEncoder.encode(password), null);
        try {
            accountDao.insert(account);
        } catch (DuplicateKeyException e) {
            session.send(new LoginAlreadyTaken(login));
            return;
        }

        session.attachAccount(account);
        authWorld.enterWorld(session);

        session.send(new AccountCreated(login));
        characterListAction.onReceive(session, "");
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

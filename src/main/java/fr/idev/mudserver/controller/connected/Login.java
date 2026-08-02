package fr.idev.mudserver.controller.connected;

import java.util.Optional;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.controller.authed.CharacterList;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.connected.AccountAlreadyConnected;
import fr.idev.mudserver.network.message.connected.AccountNotFound;
import fr.idev.mudserver.network.message.connected.IncorrectPassword;
import fr.idev.mudserver.network.message.connected.RequestPassword;
import fr.idev.mudserver.network.message.connected.WelcomeBack;
import fr.idev.mudserver.persistence.AccountDao;

@Component
public class Login implements ControllerHandler {

    private final AccountDao accountDao;
    private final AuthWorld authWorld;
    private final GameWorld gameWorld;
    private final PasswordEncoder passwordEncoder;
    private final CharacterList characterListAction;

    public Login(AccountDao accountDao, AuthWorld authWorld, GameWorld gameWorld, PasswordEncoder passwordEncoder,
            CharacterList characterListAction) {
        this.accountDao = accountDao;
        this.authWorld = authWorld;
        this.gameWorld = gameWorld;
        this.passwordEncoder = passwordEncoder;
        this.characterListAction = characterListAction;
    }

    @Override
    public String name() {
        return "login";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.CONNECTED);
    }

    @Override
    public void onReceive(Connection session, String argument) {
        String login = argument.trim();
        if (login.isEmpty()) {
            session.send(new Usage("login <name>"));
            return;
        }

        Optional<Account> account = accountDao.findByLogin(login);
        if (account.isEmpty()) {
            session.send(new AccountNotFound(login));
            return;
        }

        session.requestBlocking(new RequestPassword(),
                password -> onPasswordEntered(session, account.get(), login, password));
    }

    private void onPasswordEntered(Connection session, Account account, String login, String password) {
        if (!passwordEncoder.matches(password, account.password())) {
            session.send(new IncorrectPassword());
            return;
        }

        if (authWorld.isAlreadyConnected(account.id()) || gameWorld.isAlreadyConnected(account.id())) {
            session.send(new AccountAlreadyConnected(login));
            return;
        }

        authWorld.enterWorld(session, account);

        session.send(new WelcomeBack(login));
        characterListAction.onReceive(session, "");
    }
}

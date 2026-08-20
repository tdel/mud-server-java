package fr.idev.mudserver.network.command.connected;

import java.util.List;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.network.CommandHandler;
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

@Component
public class Register implements CommandHandler {

    private final AuthWorld authWorld;

    public Register(AuthWorld authWorld) {
        this.authWorld = authWorld;
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

        if (authWorld.findOneAccountByLogin(login).isPresent()) {
            connection.send(new LoginAlreadyTaken(login));
            return;
        }

        connection.requestBlocking(new RequestPassword(), password -> onPasswordEntered(connection, login, password));
    }

    private void onPasswordEntered(Connection connection, String login, String password) {
        List<String> reasons = authWorld.validatePassword(password);
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

        Account account;
        try {
            account = authWorld.registerAccount(connection, login, password);
        } catch (DuplicateKeyException e) {
            connection.send(new LoginAlreadyTaken(login));
            return;
        }

    }
}

package app.network.command.connected;

import java.util.List;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.game.AuthWorld;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.connected.InvalidPassword;
import app.network.message.connected.LoginAlreadyTaken;
import app.network.message.connected.PasswordMismatch;

@Component
public class Register implements CommandHandler {

    private static final String USAGE = "register <name>|<password>|<confirmation>";

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
        String[] parts = argument.split("\\|", -1);
        if (parts.length != 3) {
            connection.send(new Usage(USAGE));
            return;
        }

        String login = parts[0].trim();
        String password = parts[1];
        String confirmation = parts[2];
        if (login.isEmpty()) {
            connection.send(new Usage(USAGE));
            return;
        }

        if (authWorld.findOneAccountByLogin(login).isPresent()) {
            connection.send(new LoginAlreadyTaken(login));
            return;
        }

        List<String> reasons = authWorld.validatePassword(password);
        if (!reasons.isEmpty()) {
            connection.send(new InvalidPassword(reasons));
            return;
        }

        if (!password.equals(confirmation)) {
            connection.send(new PasswordMismatch());
            return;
        }

        try {
            authWorld.registerAccount(connection, login, password);
        } catch (DuplicateKeyException e) {
            connection.send(new LoginAlreadyTaken(login));
        }
    }
}

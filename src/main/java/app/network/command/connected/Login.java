package app.network.command.connected;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.domain.Account;
import app.game.AuthWorld;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.connected.AccountAlreadyConnected;
import app.network.message.connected.AccountNotFound;
import app.network.message.connected.IncorrectPassword;

@Component
public class Login implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(Login.class);
    private static final String USAGE = "login <name>|<password>";

    private final AuthWorld authWorld;

    public Login(AuthWorld authWorld) {
        this.authWorld = authWorld;
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
    public void onReceive(Connection connection, String argument) {
        String[] parts = argument.split("\\|", -1);
        if (parts.length != 2) {
            connection.send(new Usage(USAGE));
            return;
        }

        String login = parts[0].trim();
        String password = parts[1];
        if (login.isEmpty() || password.isEmpty()) {
            connection.send(new Usage(USAGE));
            return;
        }

        Optional<Account> account = authWorld.findOneAccountByLogin(login);
        if (account.isEmpty()) {
            log.warn("auth.login_failed account={} reason=unknown_account", login);
            connection.send(new AccountNotFound(login));
            return;
        }

        if (!authWorld.checkPassword(account.get(), password)) {
            log.warn("auth.login_failed account={} reason=bad_password", login);
            connection.send(new IncorrectPassword());
            return;
        }

        if (!authWorld.tryEnterWorld(connection, account.get())) {
            log.warn("auth.login_failed account={} reason=already_connected", login);
            connection.send(new AccountAlreadyConnected(login));
            return;
        }

        log.info("auth.login_succeeded account={}", login);
    }
}

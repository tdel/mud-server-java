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

        // TODO race check-then-act : deux connexions authentifiant le même compte au
        // même instant peuvent toutes deux passer ce test avant que l'une ou l'autre
        // ne s'enregistre ci-dessous — l'exclusivité connexion<->compte n'est donc pas
        // réellement garantie. Plusieurs invariants du domaine en dépendent
        // silencieusement (un seul virtual thread pilote un GamePlayer à la fois — voir
        // GamePlayer.equipItem/unequipItem) sans que rien ne les protège
        // vraiment aujourd'hui si ce compte est doublé. À corriger en rendant cet
        // enregistrement atomique (ex. ConcurrentHashMap.putIfAbsent par accountId
        // dans AuthWorld) plutôt qu'un scan puis un put séparé.
        if (authWorld.isAlreadyConnected(account.get().getId())) {
            log.warn("auth.login_failed account={} reason=already_connected", login);
            connection.send(new AccountAlreadyConnected(login));
            return;
        }

        authWorld.enterWorld(connection, account.get());
        log.info("auth.login_succeeded account={}", login);
    }
}

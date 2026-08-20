package fr.idev.mudserver.controller.connected;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.connected.AccountAlreadyConnected;
import fr.idev.mudserver.network.message.connected.AccountNotFound;
import fr.idev.mudserver.network.message.connected.IncorrectPassword;
import fr.idev.mudserver.network.message.connected.RequestPassword;
import fr.idev.mudserver.network.message.connected.WelcomeBack;

@Component
public class Login implements ControllerHandler {

    private static final Logger log = LoggerFactory.getLogger(Login.class);

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
        String login = argument.trim();
        if (login.isEmpty()) {
            connection.send(new Usage("login <name>"));
            return;
        }

        Optional<Account> account = authWorld.findOneAccountByLogin(login);
        if (account.isEmpty()) {
            log.warn("auth.login_failed account={} reason=unknown_account", login);
            connection.send(new AccountNotFound(login));
            return;
        }

        connection.requestBlocking(new RequestPassword(),
                password -> onPasswordEntered(connection, account.get(), login, password));
    }

    private void onPasswordEntered(Connection connection, Account account, String login, String password) {
        if (!authWorld.checkPassword(account, password)) {
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
        if (authWorld.isAlreadyConnected(account.getId())) {
            log.warn("auth.login_failed account={} reason=already_connected", login);
            connection.send(new AccountAlreadyConnected(login));
            return;
        }

        authWorld.enterWorld(connection, account);
        log.info("auth.login_succeeded account={}", login);
    }
}

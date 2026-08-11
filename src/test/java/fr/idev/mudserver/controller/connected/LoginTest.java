package fr.idev.mudserver.controller.connected;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.controller.RecordingConnection;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.connected.AccountAlreadyConnected;
import fr.idev.mudserver.network.message.connected.AccountNotFound;
import fr.idev.mudserver.network.message.connected.IncorrectPassword;
import fr.idev.mudserver.network.message.connected.RequestPassword;
import fr.idev.mudserver.network.message.connected.WelcomeBack;
import fr.idev.mudserver.persistence.AccountDao;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RecordingConnection#queueAnswer} pilote le prompt bloquant du mot de
 * passe ({@code requestBlocking}), exactement comme une vraie connexion telnet
 * répondrait au prompt précédent avant que le suivant ne soit émis.
 */
@Transactional
class LoginTest extends AbstractIntegrationTest {

    @Autowired
    private Login login;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private AuthWorld authWorld;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void emptyArgumentSendsUsage() {
        RecordingConnection connection = new RecordingConnection();

        login.onReceive(connection, "");

        assertThat(connection.received).containsExactly(new Usage("login <name>"));
    }

    @Test
    void unknownLoginSendsAccountNotFound() {
        RecordingConnection connection = new RecordingConnection();

        login.onReceive(connection, "Ghost");

        assertThat(connection.received).containsExactly(new AccountNotFound("Ghost"));
    }

    @Test
    void correctPasswordSendsWelcomeBackAndLandsInLobby() {
        createAccount("Alice", "secret");
        RecordingConnection connection = new RecordingConnection();
        connection.queueAnswer("secret");

        login.onReceive(connection, "Alice");

        assertThat(connection.received).containsExactly(new RequestPassword(), new WelcomeBack("Alice"));
        assertThat(connection.state()).isEqualTo(ConnectionState.LOBBY);
    }

    @Test
    void incorrectPasswordSendsIncorrectPassword() {
        createAccount("Carl", "secret");
        RecordingConnection connection = new RecordingConnection();
        connection.queueAnswer("wrong");

        login.onReceive(connection, "Carl");

        assertThat(connection.received).containsExactly(new RequestPassword(), new IncorrectPassword());
    }

    @Test
    void accountAlreadyConnectedSendsAccountAlreadyConnected() {
        Account account = createAccount("Bob", "secret");
        authWorld.enterWorld(new RecordingConnection(), account);
        RecordingConnection connection = new RecordingConnection();
        connection.queueAnswer("secret");

        login.onReceive(connection, "Bob");

        assertThat(connection.received).containsExactly(new RequestPassword(), new AccountAlreadyConnected("Bob"));
    }

    private Account createAccount(String accountLogin, String rawPassword) {
        Account account = new Account(UUID.randomUUID(), accountLogin, passwordEncoder.encode(rawPassword), null);
        accountDao.insert(account);
        return account;
    }
}

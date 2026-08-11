package fr.idev.mudserver.controller.connected;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.controller.RecordingConnection;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.network.ConnectionState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Couvre les règles métier compte que {@link AuthWorld} porte désormais pour le
 * compte de {@link Register}/{@link Login} — voir {@link LoginTest} pour la
 * couverture du flux telnet complet.
 */
@Transactional
class AuthWorldTest extends AbstractIntegrationTest {

    @Autowired
    private AuthWorld authWorld;

    @Test
    void findOneAccountByLoginFindsExistingAccount() {
        authWorld.registerAccount(new RecordingConnection(), "Alice", "supersecret");

        Optional<Account> found = authWorld.findOneAccountByLogin("Alice");

        assertThat(found).isPresent();
        assertThat(found.get().getLogin()).isEqualTo("Alice");
    }

    @Test
    void findOneAccountByLoginReturnsEmptyWhenUnknown() {
        assertThat(authWorld.findOneAccountByLogin("Ghost")).isEmpty();
    }

    @Test
    void registerAccountCreatesAccountAndEntersLobby() {
        RecordingConnection connection = new RecordingConnection();

        Account account = authWorld.registerAccount(connection, "Bob", "supersecret");

        assertThat(account.getLogin()).isEqualTo("Bob");
        assertThat(connection.state()).isEqualTo(ConnectionState.LOBBY);
        assertThat(authWorld.findOneAccountByLogin("Bob")).isPresent();
    }

    @Test
    void registerAccountThrowsOnDuplicateLogin() {
        authWorld.registerAccount(new RecordingConnection(), "Carl", "supersecret");

        assertThatThrownBy(() -> authWorld.registerAccount(new RecordingConnection(), "Carl", "otherSecret"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void checkPasswordMatchesCorrectPassword() {
        Account account = authWorld.registerAccount(new RecordingConnection(), "Dana", "supersecret");

        assertThat(authWorld.checkPassword(account, "supersecret")).isTrue();
        assertThat(authWorld.checkPassword(account, "wrong")).isFalse();
    }

    @Test
    void validatePasswordRejectsBlankPassword() {
        assertThat(authWorld.validatePassword("")).contains("This value should not be blank.");
    }

    @Test
    void validatePasswordRejectsTooShortPassword() {
        List<String> reasons = authWorld.validatePassword("short1");

        assertThat(reasons).containsExactly("This value is too short. It should have 8 characters or more.");
    }

    @Test
    void validatePasswordRejectsTooLongPassword() {
        List<String> reasons = authWorld.validatePassword("a".repeat(129));

        assertThat(reasons).containsExactly("This value is too long. It should have 128 characters or less.");
    }

    @Test
    void validatePasswordAcceptsValidPassword() {
        assertThat(authWorld.validatePassword("supersecret")).isEmpty();
    }
}

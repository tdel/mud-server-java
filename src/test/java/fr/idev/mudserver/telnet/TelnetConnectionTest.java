package fr.idev.mudserver.telnet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;

import fr.idev.mudserver.controller.ControllerDispatcher;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.network.SecureOutputMessage;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.connected.RequestPassword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aucun mock (le projet n'utilise pas Mockito, voir CLAUDE.md) : {@code
 * ControllerDispatcher}/{@code AuthWorld}/{@code GameWorld} sont de simples
 * classes concrètes, doublées ici par sous-classement (constructeur appelé avec
 * des dépendances {@code null}, jamais touchées puisque la méthode utilisée par
 * {@link TelnetConnection} est entièrement redéfinie) — même philosophie que le
 * {@code RecordingConnection} déjà utilisé ailleurs dans la suite de tests.
 * {@link EmbeddedChannel} sert de {@code Channel} réel (le double de test
 * officiel de Netty), pas un mock non plus.
 */
class TelnetConnectionTest {

    private final EmbeddedChannel channel = new EmbeddedChannel();
    private final RecordingDispatcher dispatcher = new RecordingDispatcher();
    private final RecordingAuthWorld authWorld = new RecordingAuthWorld();
    private final RecordingGameWorld gameWorld = new RecordingGameWorld();
    private final TelnetConnection connection = new TelnetConnection("conn-1", channel, dispatcher, authWorld,
            gameWorld);

    @Test
    void handleLineWithNoPendingPromptSplitsVerbAndArgumentAndDispatches() {
        connection.handleLine("look north");

        assertThat(dispatcher.calls).containsExactly(new RecordingDispatcher.Call("look", "north"));
    }

    @Test
    void handleLineWithNoArgumentDispatchesWithEmptyArgument() {
        connection.handleLine("look");

        assertThat(dispatcher.calls).containsExactly(new RecordingDispatcher.Call("look", ""));
    }

    @Test
    void handleLineWithArmedPendingLineConsumesLineThroughCallbackInsteadOfDispatching() {
        List<String> captured = new ArrayList<>();
        connection.requestBlocking(new Usage("pick a name"), captured::add);
        channel.outboundMessages().clear();

        connection.handleLine("Erin");

        assertThat(captured).containsExactly("Erin");
        assertThat(dispatcher.calls).isEmpty();
    }

    @Test
    void handleLineWhenDispatchThrowsWritesGenericErrorWithoutPropagating() {
        dispatcher.exceptionToThrow = new RuntimeException("boom");

        connection.handleLine("look");

        assertThat(readAllOutbound()).contains("Something went wrong processing that command. Please try again.\n");
    }

    @Test
    void handleCloseCallsGameWorldExitThenAuthWorldExitBothInvokedEvenIfGameWorldThrows() {
        gameWorld.exitWorldException = new RuntimeException("db down");

        connection.handleClose();

        assertThat(gameWorld.exitWorldCalled).isTrue();
        assertThat(authWorld.exitWorldCalled).isTrue();
    }

    @Test
    void handleCloseBothExitCallsSucceedNoExceptionPropagates() {
        connection.handleClose();

        assertThat(gameWorld.exitWorldCalled).isTrue();
        assertThat(authWorld.exitWorldCalled).isTrue();
    }

    @Test
    void requestBlockingWithNonSecureMessageArmsPendingLineWithTheGivenHandler() {
        List<String> captured = new ArrayList<>();

        connection.requestBlocking(new Usage("pick a name"), captured::add);
        connection.handleLine("Erin");

        assertThat(captured).containsExactly("Erin");
    }

    @Test
    void requestBlockingWithSecureMessageWritesEchoOffBeforeThePrompt() {
        connection.requestBlocking(new RequestPassword(), line -> {
        });

        assertThat(readAllOutboundRaw()).contains(new String(TelnetEcho.OFF, StandardCharsets.ISO_8859_1));
    }

    @Test
    void requestBlockingSecureAnswerWritesEchoOnAndNewlineBeforeDelegatingToTheHandler() {
        List<String> captured = new ArrayList<>();
        connection.requestBlocking(new RequestPassword(), captured::add);
        channel.outboundMessages().clear();

        connection.handleLine("s3cr3t");

        String raw = readAllOutboundRaw();
        assertThat(raw).contains(new String(TelnetEcho.ON, StandardCharsets.ISO_8859_1));
        assertThat(raw).contains("\n");
        assertThat(captured).containsExactly("s3cr3t");
    }

    @Test
    void sendWithOutputTelnetMessageCallsToTelnetThenWritesThePromptSuffix() {
        connection.send(new Usage("look"));

        assertThat(readAllOutbound()).isEqualTo("Usage: look\n> ");
    }

    @Test
    void sendWithNonOutputTelnetMessageThrowsIllegalArgumentException() {
        OutputMessage notTelnet = new OutputMessage() {
        };

        assertThatThrownBy(() -> connection.send(notTelnet)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void writeFlushesTextToTheChannel() {
        connection.write("hello\n");

        assertThat(readAllOutbound()).isEqualTo("hello\n");
    }

    private String readAllOutbound() {
        return readAllOutboundRaw();
    }

    private String readAllOutboundRaw() {
        StringBuilder text = new StringBuilder();
        ByteBuf buf;
        while ((buf = channel.readOutbound()) != null) {
            text.append(buf.toString(StandardCharsets.ISO_8859_1));
            buf.release();
        }
        return text.toString();
    }

    private static final class RecordingDispatcher extends ControllerDispatcher {

        record Call(String actionName, String argument) {
        }

        private final List<Call> calls = new ArrayList<>();
        private RuntimeException exceptionToThrow;

        RecordingDispatcher() {
            super(null, null);
        }

        @Override
        public void dispatch(Connection connection, String actionName, String argument) {
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            calls.add(new Call(actionName, argument));
        }
    }

    private static final class RecordingAuthWorld extends AuthWorld {

        private boolean exitWorldCalled;

        RecordingAuthWorld() {
            super(null, null);
        }

        @Override
        public void exitWorld(Connection connection) {
            exitWorldCalled = true;
        }
    }

    private static final class RecordingGameWorld extends GameWorld {

        private boolean exitWorldCalled;
        private RuntimeException exitWorldException;

        RecordingGameWorld() {
            super(null, null, null);
        }

        @Override
        public void exitWorld(Connection connection) {
            exitWorldCalled = true;
            if (exitWorldException != null) {
                throw exitWorldException;
            }
        }
    }
}

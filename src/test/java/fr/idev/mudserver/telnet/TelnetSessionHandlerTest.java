package fr.idev.mudserver.telnet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;

import fr.idev.mudserver.controller.ControllerDispatcher;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.network.Connection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code channelActive} soumet {@code runConnectionLoop} (une boucle bloquante)
 * à l'exécuteur : un exécuteur synchrone ferait deadlocker le test (le thread
 * appelant resterait bloqué dans {@code inbox.take()}). Un vrai exécuteur de
 * threads virtuels est donc utilisé, comme en production, avec des
 * {@link CountDownLatch} pour synchroniser les assertions avec le thread
 * consommateur asynchrone plutôt que d'attendre en dur.
 */
class TelnetSessionHandlerTest {

    private final RecordingDispatcher dispatcher = new RecordingDispatcher();
    private final RecordingAuthWorld authWorld = new RecordingAuthWorld();
    private final RecordingGameWorld gameWorld = new RecordingGameWorld();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void channelActiveWritesWelcomeMessage() {
        EmbeddedChannel channel = openChannel();
        dispatcher.releaseLatch.countDown();

        channel.writeInbound("look");
        await(dispatcher.processedLatch);

        assertThat(readAllOutbound(channel)).contains("Welcome to mud-server-java.");
    }

    @Test
    void channelReadDoesNotDispatchOnTheIoThreadBeforeTheConsumerLoopRuns() {
        EmbeddedChannel channel = openChannel();
        channel.outboundMessages().clear();

        channel.writeInbound("look");

        assertThat(dispatcher.calls).isEmpty();

        dispatcher.releaseLatch.countDown();
        await(dispatcher.processedLatch);
        assertThat(dispatcher.calls).hasSize(1);
    }

    @Test
    void channelInactivePushesPoisonPillAndTriggersHandleClose() {
        EmbeddedChannel channel = openChannel();
        dispatcher.releaseLatch.countDown();

        channel.close();
        await(authWorld.exitWorldLatch);

        assertThat(gameWorld.exitWorldCalled).isTrue();
        assertThat(authWorld.exitWorldCalled).isTrue();
    }

    @Test
    void connectionLoopSetsMdcConnectionIdWhileProcessingALine() {
        EmbeddedChannel channel = openChannel();
        dispatcher.releaseLatch.countDown();

        channel.writeInbound("look");
        await(dispatcher.processedLatch);

        assertThat(dispatcher.observedConnectionIdWhenCalled).startsWith("conn-");
    }

    @Test
    void multipleLinesEnqueuedBeforeTheConsumerRunsAreAllProcessedInOrder() {
        EmbeddedChannel channel = openChannel();
        channel.outboundMessages().clear();
        channel.writeInbound("look");
        channel.writeInbound("go");
        channel.writeInbound("stats");

        dispatcher.releaseLatch.countDown();
        await(dispatcher.allProcessedLatch);

        assertThat(dispatcher.calls).extracting(RecordingDispatcher.Call::actionName).containsExactly("look", "go",
                "stats");
    }

    private EmbeddedChannel openChannel() {
        return new EmbeddedChannel(new TelnetSessionHandler(executor, dispatcher, authWorld, gameWorld));
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(2, TimeUnit.SECONDS)).as("background virtual thread should have progressed")
                    .isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private String readAllOutbound(EmbeddedChannel channel) {
        StringBuilder text = new StringBuilder();
        ByteBuf buf;
        while ((buf = channel.readOutbound()) != null) {
            text.append(buf.toString(StandardCharsets.ISO_8859_1));
            buf.release();
        }
        return text.toString();
    }

    /**
     * Bloque avant d'enregistrer chaque appel : tant que {@code releaseLatch} n'est
     * pas décompté par le test, {@code calls} reste vide de façon déterministe (pas
     * une simple fenêtre de course), ce qui permet de prouver que
     * {@code channelRead0} n'a rien déclenché de synchrone.
     */
    private static final class RecordingDispatcher extends ControllerDispatcher {

        record Call(String actionName, String argument) {
        }

        private final List<Call> calls = new ArrayList<>();
        private final CountDownLatch releaseLatch = new CountDownLatch(1);
        private final CountDownLatch processedLatch = new CountDownLatch(1);
        private final CountDownLatch allProcessedLatch = new CountDownLatch(3);
        private volatile String observedConnectionIdWhenCalled;

        RecordingDispatcher() {
            super(null);
        }

        @Override
        public void dispatch(Connection connection, String actionName, String argument) {
            try {
                releaseLatch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            observedConnectionIdWhenCalled = MDC.get("connectionId");
            calls.add(new Call(actionName, argument));
            processedLatch.countDown();
            allProcessedLatch.countDown();
        }
    }

    private static final class RecordingAuthWorld extends AuthWorld {

        private final CountDownLatch exitWorldLatch = new CountDownLatch(1);
        private boolean exitWorldCalled;

        RecordingAuthWorld() {
            super(null, null);
        }

        @Override
        public void exitWorld(Connection connection) {
            exitWorldCalled = true;
            exitWorldLatch.countDown();
        }
    }

    private static final class RecordingGameWorld extends GameWorld {

        private boolean exitWorldCalled;

        RecordingGameWorld() {
            super(null, null, null);
        }

        @Override
        public void exitWorld(Connection connection) {
            exitWorldCalled = true;
        }
    }
}

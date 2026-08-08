package fr.idev.mudserver.controller;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.OutputMessage;

/**
 * Double de test partagé pour {@link Connection} : enregistre tout ce qui est
 * envoyé, et permet de piloter les scénarios multi-prompts ({@code
 * requestBlocking} de {@code Login}/{@code CharacterCreate}) via
 * {@link #queueAnswer(String)} — chaque réponse mise en file est consommée
 * immédiatement par le prochain {@code requestBlocking}, comme le ferait une
 * vraie connexion telnet répondant au prompt précédent avant que le suivant ne
 * soit émis.
 */
public class RecordingConnection implements Connection {

    public final List<OutputMessage> received = new ArrayList<>();
    private final Deque<String> queuedAnswers = new ArrayDeque<>();
    private ConnectionState state = ConnectionState.INGAME;

    public void queueAnswer(String answer) {
        queuedAnswers.add(answer);
    }

    @Override
    public void requestBlocking(OutputMessage message, Consumer<String> handler) {
        received.add(message);
        if (!queuedAnswers.isEmpty()) {
            handler.accept(queuedAnswers.poll());
        }
    }

    @Override
    public ConnectionState state() {
        return state;
    }

    @Override
    public void setState(ConnectionState state) {
        this.state = state;
    }

    @Override
    public void send(OutputMessage message) {
        received.add(message);
    }

    @Override
    public void close() {
        // non utilisé par ces tests
    }
}

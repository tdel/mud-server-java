package fr.idev.mudserver.telnet;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

import fr.idev.mudserver.controller.ControllerDispatcher;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.network.SecureOutputMessage;

/**
 * État par connexion, porté par un attribut de {@link Channel}. Netty garantit
 * qu'un seul virtual thread touche une connexion donnée à la fois (voir
 * GameCommandHandler) — aucune synchronisation supplémentaire n'est donc
 * nécessaire sur ces champs mutables.
 */
public class TelnetConnection implements Connection, TelnetOutput {

    private static final Logger log = LoggerFactory.getLogger(TelnetConnection.class);

    private final String connectionId;
    private final Channel channel;
    private final ControllerDispatcher controllerDispatcher;
    private final AuthWorld authWorld;
    private final GameWorld gameWorld;

    private ConnectionState state = ConnectionState.CONNECTED;
    private Consumer<String> pendingLine;
    private boolean pendingLineSecure;

    public TelnetConnection(String connectionId, Channel channel, ControllerDispatcher controllerDispatcher,
            AuthWorld authWorld, GameWorld gameWorld) {
        this.connectionId = connectionId;
        this.channel = channel;
        this.controllerDispatcher = controllerDispatcher;
        this.authWorld = authWorld;
        this.gameWorld = gameWorld;
    }

    /**
     * Identifiant court et lisible, généré une fois par connexion par
     * {@link TelnetSessionHandler#channelActive} — sert uniquement de clé de
     * corrélation MDC pour les logs, aucune signification métier.
     */
    public String getConnectionId() {
        return connectionId;
    }

    public void handleLine(String rawLine) {
        boolean secureLine = false;
        String verb = null;
        try {
            if (pendingLine != null) {
                Consumer<String> handler = pendingLine;
                secureLine = pendingLineSecure;
                pendingLine = null;
                pendingLineSecure = false;
                handler.accept(rawLine);
                return;
            }

            String line = rawLine.trim();
            int spaceIdx = line.indexOf(' ');
            String name = spaceIdx == -1 ? line : line.substring(0, spaceIdx);
            String argument = spaceIdx == -1 ? "" : line.substring(spaceIdx + 1);

            verb = name.toLowerCase();
            controllerDispatcher.dispatch(this, verb, argument);
        } catch (Exception e) {
            log.error("telnet.command.failed verb={} line={}", verb, secureLine ? "[REDACTED]" : rawLine, e);
            write("Something went wrong processing that command. Please try again.\n");
        }
    }

    public void handleClose() {
        try {
            gameWorld.exitWorld(this);
        } catch (Exception e) {
            log.error("telnet.disconnect_cleanup_failed stage=game", e);
        }
        try {
            authWorld.exitWorld(this);
        } catch (Exception e) {
            log.error("telnet.disconnect_cleanup_failed stage=auth", e);
        }
    }

    /**
     * Si {@code message} est un {@link SecureOutputMessage}, coupe l'echo local du
     * client avant l'envoi et capture la ligne suivante en clair : l'echo est
     * toujours rétabli juste avant que {@code handler} ne s'exécute. Le client
     * n'ayant jamais renvoyé le retour chariot pendant que l'echo était coupé, on
     * émet nous-même un saut de ligne pour que ce que {@code handler} écrit démarre
     * sur une ligne neuve plutôt qu'accolé au prompt.
     */
    @Override
    public void requestBlocking(OutputMessage message, Consumer<String> handler) {
        this.send(message);
        if (message instanceof SecureOutputMessage) {
            writeRaw(TelnetEcho.OFF);
            this.pendingLineSecure = true;
            this.pendingLine = line -> {
                writeRaw(TelnetEcho.ON);
                write("\n");
                handler.accept(line);
            };
        } else {
            this.pendingLineSecure = false;
            this.pendingLine = handler;
        }
    }

    @Override
    public void send(OutputMessage message) {
        if (!(message instanceof OutputTelnetMessage telnetMessage)) {
            throw new IllegalArgumentException("Message non supporté par le transport telnet : " + message);
        }
        telnetMessage.toTelnet(this);
        write("> ");
    }

    @Override
    public void write(String text) {
        channel.writeAndFlush(Unpooled.copiedBuffer(text, StandardCharsets.UTF_8));
    }

    private void writeRaw(byte[] bytes) {
        channel.writeAndFlush(Unpooled.wrappedBuffer(bytes));
    }

    @Override
    public void close() {
        channel.close();
    }

    @Override
    public ConnectionState state() {
        return state;
    }

    @Override
    public void setState(ConnectionState state) {
        this.state = state;
    }
}

package fr.idev.mudserver.telnet;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.PlayerInstance;
import fr.idev.mudserver.network.ActionDispatcher;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.network.Session;

/**
 * État par connexion, porté par un attribut de {@link Channel}. Netty garantit
 * qu'un seul virtual thread touche une connexion donnée à la fois (voir
 * GameCommandHandler) — aucune synchronisation supplémentaire n'est donc
 * nécessaire sur ces champs mutables.
 */
public class TelnetSession implements Session, TelnetOutput {

    private static final Logger log = LoggerFactory.getLogger(TelnetSession.class);

    private final Channel channel;
    private final ActionDispatcher actionDispatcher;
    private final AuthWorld authWorld;
    private final GameWorld gameWorld;

    private ConnectionState state = ConnectionState.CONNECTED;
    private Account account;
    private PlayerInstance player;
    private Consumer<String> pendingLine;

    public TelnetSession(Channel channel, ActionDispatcher actionDispatcher, AuthWorld authWorld, GameWorld gameWorld) {
        this.channel = channel;
        this.actionDispatcher = actionDispatcher;
        this.authWorld = authWorld;
        this.gameWorld = gameWorld;
    }

    public void handleLine(String rawLine) {
        try {
            if (pendingLine != null) {
                Consumer<String> handler = pendingLine;
                pendingLine = null;
                handler.accept(rawLine);
                return;
            }

            String line = rawLine.trim();
            int spaceIdx = line.indexOf(' ');
            String name = spaceIdx == -1 ? line : line.substring(0, spaceIdx);
            String argument = spaceIdx == -1 ? "" : line.substring(spaceIdx + 1);

            actionDispatcher.dispatch(this, name.toLowerCase(), argument);
        } catch (Exception e) {
            log.error("telnet.command.failed line={}", rawLine, e);
            write("Something went wrong processing that command. Please try again.\n");
        }
    }

    public void handleClose() {
        if (player != null) {
            gameWorld.exitWorld(player);
        }
        authWorld.exitWorld(this);
    }

    @Override
    public void awaitLine(Consumer<String> handler) {
        this.pendingLine = handler;
    }

    /**
     * Coupe l'echo local du client, écrit {@code prompt}, capture la ligne suivante
     * en clair pour {@code onLine}. L'echo est toujours rétabli juste avant que
     * {@code onLine} ne s'exécute. Le client n'ayant jamais renvoyé le retour
     * chariot pendant que l'echo était coupé, on émet nous-même un saut de ligne
     * pour que ce que {@code onLine} écrit démarre sur une ligne neuve plutôt
     * qu'accolé au prompt.
     */
    @Override
    public void promptMasked(String prompt, Consumer<String> onLine) {
        writeRaw(TelnetEcho.OFF);
        write(prompt);
        awaitLine(line -> {
            writeRaw(TelnetEcho.ON);
            write("\n");
            onLine.accept(line);
        });
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
        switch (state) {
            case CONNECTED -> {
                account = null;
                player = null;
            }
            case AUTHED -> player = null;
            case INGAME -> {
            }
        }
    }

    @Override
    public Account account() {
        return account;
    }

    @Override
    public void attachAccount(Account account) {
        this.account = account;
    }

    @Override
    public PlayerInstance player() {
        return player;
    }

    @Override
    public void attachPlayer(PlayerInstance player) {
        this.player = player;
    }
}

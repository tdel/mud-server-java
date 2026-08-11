package fr.idev.mudserver.telnet;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

import fr.idev.mudserver.controller.ControllerDispatcher;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.network.SecureOutputMessage;

public class TelnetConnection implements Connection, TelnetOutput {

    private static final Logger log = LoggerFactory.getLogger(TelnetConnection.class);

    private final String connectionId;
    private final Channel channel;
    private final ControllerDispatcher controllerDispatcher;
    private final AuthWorld authWorld;
    private final WorldInstanceService worldInstanceService;

    private ConnectionState state = ConnectionState.CONNECTED;
    private GamePlayer character;
    private Account account;
    private WorldInstance worldInstance;
    private Consumer<String> pendingLine;
    private boolean pendingLineSecure;

    public TelnetConnection(String connectionId, Channel channel, ControllerDispatcher controllerDispatcher,
            AuthWorld authWorld, WorldInstanceService worldInstanceService) {
        this.connectionId = connectionId;
        this.channel = channel;
        this.controllerDispatcher = controllerDispatcher;
        this.authWorld = authWorld;
        this.worldInstanceService = worldInstanceService;
    }

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
            worldInstanceService.exitGame(this);
        } catch (Exception e) {
            log.error("telnet.disconnect_cleanup_failed stage=game", e);
        }
        try {
            worldInstanceService.exitCharSelect(this);
        } catch (Exception e) {
            log.error("telnet.disconnect_cleanup_failed stage=charselect", e);
        }
        try {
            authWorld.exitWorld(this);
        } catch (Exception e) {
            log.error("telnet.disconnect_cleanup_failed stage=auth", e);
        }
    }

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

    @Override
    public void setCharacter(GamePlayer character) {
        this.character = character;
    }

    @Override
    public GamePlayer character() {
        if (state != ConnectionState.INGAME) {
            throw new IllegalStateException("Connection " + connectionId + " n'est pas en état INGAME (" + state + ")");
        }
        return character;
    }

    @Override
    public void setAccount(Account account) {
        this.account = account;
    }

    @Override
    public Account account() {
        if (state == ConnectionState.CONNECTED) {
            throw new IllegalStateException("Connection " + connectionId + " n'est pas authentifiée (" + state + ")");
        }
        return account;
    }

    @Override
    public void setWorldInstance(WorldInstance worldInstance) {
        this.worldInstance = worldInstance;
    }

    @Override
    public WorldInstance worldInstance() {
        if (state == ConnectionState.CONNECTED || state == ConnectionState.LOBBY) {
            throw new IllegalStateException(
                    "Connection " + connectionId + " n'a pas de WorldInstance en état " + state);
        }
        return worldInstance;
    }
}

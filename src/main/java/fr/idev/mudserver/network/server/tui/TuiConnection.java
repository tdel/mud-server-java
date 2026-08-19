package fr.idev.mudserver.network.server.tui;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

import fr.idev.mudserver.controller.ControllerDispatcher;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.world.WorldInstance;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.network.SecureOutputMessage;

public class TuiConnection implements Connection, JsonOutput {

    private static final Logger log = LoggerFactory.getLogger(TuiConnection.class);

    private final String connectionId;
    private final Channel channel;
    private final ObjectMapper objectMapper;
    private final ControllerDispatcher controllerDispatcher;
    private final AuthWorld authWorld;
    private final WorldInstanceService worldInstanceService;

    private ConnectionState state = ConnectionState.CONNECTED;
    private CharacterInstance player;
    private Account account;
    private WorldInstance worldInstance;
    private Consumer<String> pendingLine;
    private boolean pendingLineSecure;

    public TuiConnection(String connectionId, Channel channel, ObjectMapper objectMapper,
            ControllerDispatcher controllerDispatcher, AuthWorld authWorld, WorldInstanceService worldInstanceService) {
        this.connectionId = connectionId;
        this.channel = channel;
        this.objectMapper = objectMapper;
        this.controllerDispatcher = controllerDispatcher;
        this.authWorld = authWorld;
        this.worldInstanceService = worldInstanceService;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public void handleLine(String rawLine) {
        boolean secureLine = false;
        try {
            if (pendingLine != null) {
                Consumer<String> handler = pendingLine;
                secureLine = pendingLineSecure;
                pendingLine = null;
                pendingLineSecure = false;
                TuiReply reply = objectMapper.readValue(rawLine, TuiReply.class);
                handler.accept(reply.reply());
                return;
            }

            TuiCommand command = objectMapper.readValue(rawLine, TuiCommand.class);
            String verb = command.verb() == null ? "" : command.verb().toLowerCase();
            String argument = command.argument() == null ? "" : command.argument();
            controllerDispatcher.dispatch(this, verb, argument);
        } catch (Exception e) {
            log.error("tui.command.failed line={}", secureLine ? "[REDACTED]" : rawLine, e);
            write("Error", Map.of("message", "Something went wrong processing that command. Please try again."), false);
        }
    }

    public void handleClose() {
        boolean wasInLobby = state == ConnectionState.LOBBY;
        try {
            worldInstanceService.exitGame(this);
        } catch (Exception e) {
            log.error("tui.disconnect_cleanup_failed stage=game", e);
        }
        try {
            this.detachWorldInstance();
        } catch (Exception e) {
            log.error("tui.disconnect_cleanup_failed stage=charselect", e);
        }
        if (wasInLobby) {
            try {
                authWorld.leaveLobby(this);
            } catch (Exception e) {
                log.error("tui.disconnect_cleanup_failed stage=lobby_notify", e);
            }
        }
        try {
            authWorld.exitWorld(this);
        } catch (Exception e) {
            log.error("tui.disconnect_cleanup_failed stage=auth", e);
        }
    }

    @Override
    public void requestBlocking(OutputMessage message, Consumer<String> handler) {
        this.send(message);
        this.pendingLineSecure = message instanceof SecureOutputMessage;
        this.pendingLine = handler;
    }

    @Override
    public void send(OutputMessage message) {
        if (!(message instanceof OutputJsonMessage jsonMessage)) {
            throw new IllegalArgumentException("Message non supporté par le transport TUI : " + message);
        }
        jsonMessage.toJson(this);
    }

    @Override
    public void write(String type, Object payload, boolean secure) {
        try {
            String json = objectMapper.writeValueAsString(new TuiEnvelope(type, payload, secure));
            channel.writeAndFlush(Unpooled.copiedBuffer(json + "\n", StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("tui.serialize_failed type={}", type, e);
        }
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
    public void attachCharacter(CharacterInstance character) {
        this.player = character;
        character.setConnection(this);
        this.setState(ConnectionState.INGAME);
    }

    @Override
    public void detachCharacter() {
        this.player = null;
        this.setState(ConnectionState.LOBBY);
    }

    @Override
    public CharacterInstance character() {
        if (state != ConnectionState.INGAME) {
            throw new IllegalStateException("Connection " + connectionId + " n'est pas en état INGAME (" + state + ")");
        }
        return player;
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
    public void attachWorldInstance(WorldInstance worldInstance) {
        this.worldInstance = worldInstance;
        this.setState(ConnectionState.CHARSELECT);
    }

    @Override
    public void detachWorldInstance() {
        this.worldInstance = null;
        this.setState(ConnectionState.LOBBY);
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

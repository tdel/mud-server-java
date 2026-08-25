package app.network.server.tcpjson;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

import app.network.CommandDispatcher;
import app.domain.Account;
import app.domain.world.WorldInstance;
import app.domain.actor.instance.CharacterInstance;
import app.game.AuthWorld;
import app.game.WorldInstanceService;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.OutputJsonMessage;
import app.network.OutputMessage;
import app.network.SecureOutputMessage;

public class TcpJsonConnection implements Connection, TcpJsonOutput {

    private static final Logger log = LoggerFactory.getLogger(TcpJsonConnection.class);

    private final String connectionId;
    private final Channel channel;
    private final ObjectMapper objectMapper;
    private final CommandDispatcher commandDispatcher;
    private final AuthWorld authWorld;
    private final WorldInstanceService worldInstanceService;

    private ConnectionState state = ConnectionState.CONNECTED;
    private CharacterInstance player;
    private Account account;
    private WorldInstance worldInstance;
    private Consumer<String> pendingLine;
    private boolean pendingLineSecure;

    public TcpJsonConnection(String connectionId, Channel channel, ObjectMapper objectMapper,
            CommandDispatcher commandDispatcher, AuthWorld authWorld, WorldInstanceService worldInstanceService) {
        this.connectionId = connectionId;
        this.channel = channel;
        this.objectMapper = objectMapper;
        this.commandDispatcher = commandDispatcher;
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
                TcpJsonReply reply = objectMapper.readValue(rawLine, TcpJsonReply.class);
                handler.accept(reply.reply());
                return;
            }

            TcpJsonCommand command = objectMapper.readValue(rawLine, TcpJsonCommand.class);
            String verb = command.verb() == null ? "" : command.verb().toLowerCase();
            String argument = command.argument() == null ? "" : command.argument();
            commandDispatcher.dispatch(this, verb, argument);
        } catch (Exception e) {
            log.error("tcpjson.command.failed line={}", secureLine ? "[REDACTED]" : rawLine, e);
            write("Error", Map.of("message", "Something went wrong processing that command. Please try again."), false);
        }
    }

    public void handleClose() {
        try {
            worldInstanceService.exitGame(this);
        } catch (Exception e) {
            log.error("tcpjson.disconnect_cleanup_failed stage=game", e);
        }
        try {
            this.detachWorldInstance();
        } catch (Exception e) {
            log.error("tcpjson.disconnect_cleanup_failed stage=charselect", e);
        }
        try {
            authWorld.exitWorld(this);
        } catch (Exception e) {
            log.error("tcpjson.disconnect_cleanup_failed stage=auth", e);
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
            throw new IllegalArgumentException("Message non supporté par le transport TCP/JSON : " + message);
        }
        jsonMessage.toJson(this);
    }

    @Override
    public void write(String type, Object payload, boolean secure) {
        try {
            String json = objectMapper.writeValueAsString(new TcpJsonEnvelope(type, payload, secure));
            channel.writeAndFlush(Unpooled.copiedBuffer(json + "\n", StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("tcpjson.serialize_failed type={}", type, e);
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
        this.setState(ConnectionState.CONNECTED);
    }

    @Override
    public WorldInstance worldInstance() {
        if (state == ConnectionState.CONNECTED) {
            throw new IllegalStateException(
                    "Connection " + connectionId + " n'a pas de WorldInstance en état " + state);
        }
        return worldInstance;
    }
}

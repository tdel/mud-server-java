package app.network.server.tcpjson;

import java.nio.charset.StandardCharsets;
import java.util.Map;

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

public class TcpJsonConnection implements Connection, TcpJsonOutput {

    private static final Logger log = LoggerFactory.getLogger(TcpJsonConnection.class);

    private final String connectionId;
    private final Channel channel;
    private final ObjectMapper objectMapper;
    private final CommandDispatcher commandDispatcher;
    private final AuthWorld authWorld;
    private final WorldInstanceService worldInstanceService;

    // AbstractCharacter.broadcast() appelle send(message) une fois par
    // destinataire de la KnownList avec le MÊME objet message — sans ce cache,
    // write() ré-encode en JSON (Jackson) un contenu strictement identique à
    // chaque itération. Le cache est scopé par thread (et non un champ statique
    // unique) : chaque broadcast tourne entièrement sur un seul thread (celui de
    // la commande du joueur, ou l'un des threads du pool @Scheduled), donc deux
    // diffusions concurrentes sur deux threads différents ne se marchent jamais
    // dessus. L'égalité par référence sur payload est sûre par construction :
    // seule une boucle broadcast() réutilise le même objet message d'un appel à
    // l'autre ; un payload différent (y compris un DTO fraîchement construit par
    // un toJson surchargé, ex. MapEnter.Payload) invalide simplement le cache.
    private record CachedEncode(Object payloadRef, String type, byte[] json) {
    }

    private static final ThreadLocal<CachedEncode> LAST_ENCODE = new ThreadLocal<>();

    private ConnectionState state = ConnectionState.CONNECTED;
    private CharacterInstance player;
    private Account account;
    private WorldInstance worldInstance;

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
        try {
            TcpJsonCommand command = objectMapper.readValue(rawLine, TcpJsonCommand.class);
            String verb = command.verb() == null ? "" : command.verb().toLowerCase();
            String argument = command.argument() == null ? "" : command.argument();
            commandDispatcher.dispatch(this, verb, argument);
        } catch (Exception e) {
            log.error("tcpjson.command.failed line={}", rawLine, e);
            write("Error", Map.of("message", "Something went wrong processing that command. Please try again."));
        }
    }

    public void handleClose() {
        try {
            worldInstanceService.exitGame(this);
        } catch (Exception e) {
            log.error("tcpjson.disconnect_cleanup_failed stage=game", e);
        }
        try {
            authWorld.exitWorld(this);
        } catch (Exception e) {
            log.error("tcpjson.disconnect_cleanup_failed stage=auth", e);
        }
        try {
            this.detachWorldInstance();
        } catch (Exception e) {
            log.error("tcpjson.disconnect_cleanup_failed stage=charselect", e);
        }
    }

    @Override
    public void send(OutputMessage message) {
        if (!(message instanceof OutputJsonMessage jsonMessage)) {
            throw new IllegalArgumentException("Message non supporté par le transport TCP/JSON : " + message);
        }
        jsonMessage.toJson(this);
    }

    @Override
    public void write(String type, Object payload) {
        log.debug("message.sent type={} connectionId={} state={} character={} account={}", type, connectionId, state,
                state == ConnectionState.INGAME ? player.getName() : "-",
                state != ConnectionState.CONNECTED ? account.getLogin() : "-");
        try {
            byte[] json = encode(type, payload);
            channel.writeAndFlush(Unpooled.wrappedBuffer(json));
        } catch (Exception e) {
            log.error("tcpjson.serialize_failed type={}", type, e);
        }
    }

    private byte[] encode(String type, Object payload) throws Exception {
        CachedEncode cached = LAST_ENCODE.get();
        if (cached != null && cached.payloadRef() == payload && cached.type().equals(type)) {
            return cached.json();
        }
        String json = objectMapper.writeValueAsString(new TcpJsonEnvelope(type, payload));
        byte[] encoded = (json + "\n").getBytes(StandardCharsets.UTF_8);
        LAST_ENCODE.set(new CachedEncode(payload, type, encoded));
        return encoded;
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

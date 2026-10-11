package app.network.server.tcpjson;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import tools.jackson.databind.ObjectMapper;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;

import app.network.CommandDispatcher;
import app.network.message.Ping;
import app.game.AuthWorld;
import app.game.WorldInstanceService;

public class TcpJsonSessionHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(TcpJsonSessionHandler.class);

    private static final AttributeKey<TcpJsonConnection> CONNECTION_KEY = AttributeKey.valueOf("tcpJsonConnection");
    private static final AttributeKey<BlockingQueue<String>> INBOX_KEY = AttributeKey.valueOf("tcpJsonInbox");
    private static final String POISON_PILL = new String();
    private static final String MDC_CONNECTION_ID = "connectionId";
    // Voir channelRead0 : bornes de la boîte de réception d'une connexion.
    private static final int INBOX_HIGH_WATER = 256;
    private static final int INBOX_LOW_WATER = 64;

    private static final AtomicLong CONNECTION_SEQUENCE = new AtomicLong();

    private final ExecutorService virtualThreadExecutor;
    private final ObjectMapper objectMapper;
    private final CommandDispatcher commandDispatcher;
    private final AuthWorld authWorld;
    private final WorldInstanceService worldInstanceService;

    // Une instance de handler par channel (TcpJsonServerInitializer.initChannel).
    private boolean awaitingPong;

    public TcpJsonSessionHandler(ExecutorService virtualThreadExecutor, ObjectMapper objectMapper,
            CommandDispatcher commandDispatcher, AuthWorld authWorld, WorldInstanceService worldInstanceService) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.objectMapper = objectMapper;
        this.commandDispatcher = commandDispatcher;
        this.authWorld = authWorld;
        this.worldInstanceService = worldInstanceService;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String connectionId = "tcpjson-" + CONNECTION_SEQUENCE.incrementAndGet();
        log.info("tcpjson.connection_opened remote={} connectionId={}", ctx.channel().remoteAddress(), connectionId);
        TcpJsonConnection connection = new TcpJsonConnection(connectionId, ctx.channel(), objectMapper,
                commandDispatcher, authWorld, worldInstanceService);
        BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        ctx.channel().attr(CONNECTION_KEY).set(connection);
        ctx.channel().attr(INBOX_KEY).set(inbox);
        Channel channel = ctx.channel();
        virtualThreadExecutor.execute(() -> runConnectionLoop(channel, connection, inbox));
    }

    // Contre-pression : un client qui envoie plus vite que ses commandes ne sont
    // traitées (accès base sérialisés) remplirait sans fin la boîte de réception.
    // Au-delà de INBOX_HIGH_WATER lignes en attente, Netty cesse de lire la socket
    // (les octets restent dans le tampon TCP du noyau, le client finit bloqué) ;
    // runConnectionLoop relance la lecture une fois redescendu sous
    // INBOX_LOW_WATER.
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String line) {
        awaitingPong = false;
        BlockingQueue<String> inbox = ctx.channel().attr(INBOX_KEY).get();
        inbox.add(line);
        if (inbox.size() >= INBOX_HIGH_WATER && ctx.channel().config().isAutoRead()) {
            log.warn("tcpjson.inbox_backpressure remote={} pending={}", ctx.channel().remoteAddress(), inbox.size());
            ctx.channel().config().setAutoRead(false);
        }
    }

    // Heartbeat : une socket à moitié ouverte (client planté, réseau coupé, veille)
    // ne déclenche jamais channelInactive côté serveur — sans ça, le joueur reste
    // en jeu et son compte marqué connecté indéfiniment. Après READER_IDLE_SECONDS
    // de silence du client, on lui envoie un Ping ; s'il se tait encore pendant
    // le même délai, la connexion est fermée (nettoyage normal via
    // channelInactive). Tout appelé sur l'event loop du channel : pas de volatile.
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        if (!(event instanceof IdleStateEvent idle) || idle.state() != IdleState.READER_IDLE) {
            super.userEventTriggered(ctx, event);
            return;
        }
        TcpJsonConnection connection = ctx.channel().attr(CONNECTION_KEY).get();
        if (awaitingPong) {
            log.warn("tcpjson.heartbeat_timeout remote={} connectionId={}", ctx.channel().remoteAddress(),
                    connection != null ? connection.getConnectionId() : null);
            ctx.close();
            return;
        }
        awaitingPong = true;
        if (connection != null) {
            connection.send(new Ping());
        }
    }

    // Sans ce handler, une exception (reset TCP, ligne trop longue...) remonte en
    // fin de pipeline où Netty se contente de la journaliser, sans fermer le
    // channel.
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("tcpjson.channel_error remote={} cause={}", ctx.channel().remoteAddress(), cause.toString());
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("tcpjson.connection_closed remote={}", ctx.channel().remoteAddress());
        BlockingQueue<String> inbox = ctx.channel().attr(INBOX_KEY).get();
        if (inbox != null) {
            inbox.add(POISON_PILL);
        }
    }

    private void runConnectionLoop(Channel channel, TcpJsonConnection connection, BlockingQueue<String> inbox) {
        MDC.put(MDC_CONNECTION_ID, connection.getConnectionId());
        connection.write("Welcome", Map.of("message",
                "Welcome to mud-server-java. Send {\"verb\":\"login\",\"argument\":\"<name>|<password>\"} "
                        + "or {\"verb\":\"register\",\"argument\":\"<name>|<password>|<confirmation>\"} to begin."));
        try {
            while (true) {
                String line = inbox.take();
                if (line == POISON_PILL) {
                    return;
                }
                if (inbox.size() <= INBOX_LOW_WATER && !channel.config().isAutoRead()) {
                    channel.config().setAutoRead(true);
                }
                connection.handleLine(line);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            connection.handleClose();
            MDC.clear();
        }
    }
}

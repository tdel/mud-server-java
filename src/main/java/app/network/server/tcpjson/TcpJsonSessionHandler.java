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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;

import app.network.CommandDispatcher;
import app.game.AuthWorld;
import app.game.WorldInstanceService;

public class TcpJsonSessionHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(TcpJsonSessionHandler.class);

    private static final AttributeKey<TcpJsonConnection> CONNECTION_KEY = AttributeKey.valueOf("tcpJsonConnection");
    private static final AttributeKey<BlockingQueue<String>> INBOX_KEY = AttributeKey.valueOf("tcpJsonInbox");
    private static final String POISON_PILL = new String();
    private static final String MDC_CONNECTION_ID = "connectionId";

    private static final AtomicLong CONNECTION_SEQUENCE = new AtomicLong();

    private final ExecutorService virtualThreadExecutor;
    private final ObjectMapper objectMapper;
    private final CommandDispatcher commandDispatcher;
    private final AuthWorld authWorld;
    private final WorldInstanceService worldInstanceService;

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
        virtualThreadExecutor.execute(() -> runConnectionLoop(connection, inbox));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String line) {
        ctx.channel().attr(INBOX_KEY).get().add(line);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("tcpjson.connection_closed remote={}", ctx.channel().remoteAddress());
        BlockingQueue<String> inbox = ctx.channel().attr(INBOX_KEY).get();
        if (inbox != null) {
            inbox.add(POISON_PILL);
        }
    }

    private void runConnectionLoop(TcpJsonConnection connection, BlockingQueue<String> inbox) {
        MDC.put(MDC_CONNECTION_ID, connection.getConnectionId());
        connection.write("Welcome",
                Map.of("message", "Welcome to mud-server-java. Send {\"verb\":\"login\",\"argument\":\"<name>\"} "
                        + "or {\"verb\":\"register\",\"argument\":\"<name>\"} to begin."),
                false);
        try {
            while (true) {
                String line = inbox.take();
                if (line == POISON_PILL) {
                    return;
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

package fr.idev.mudserver.telnet;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;

import fr.idev.mudserver.controller.ControllerDispatcher;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.GameWorld;

/**
 * Frontière Netty <-> logique métier. Chaque connexion obtient exactement un
 * virtual thread, démarré à {@code channelActive} et vivant jusqu'à la
 * déconnexion : c'est lui, et lui seul, qui exécute
 * {@code connection.handleLine(...)}, en dépilant une file FIFO alimentée par
 * {@code channelRead0}. C'est le calque direct du "un coroutine par connexion"
 * de Swoole côté PHP (TelnetConnectionHandler::run()).
 *
 * <p>
 * Point important : {@code channelRead0} ne doit jamais soumettre
 * indépendamment chaque ligne au pool de virtual threads (un
 * {@code executor.execute()} par ligne) — deux lignes reçues dans le même
 * paquet TCP se traiteraient alors sur deux virtual threads distincts, sans
 * garantie d'ordre d'exécution entre eux. D'où la file : un seul virtual thread
 * consommateur par connexion.
 */
public class TelnetSessionHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(TelnetSessionHandler.class);

    private static final AttributeKey<TelnetConnection> CONNECTION_KEY = AttributeKey.valueOf("telnetConnection");
    private static final AttributeKey<BlockingQueue<String>> INBOX_KEY = AttributeKey.valueOf("telnetInbox");
    private static final String POISON_PILL = new String();
    private static final String MDC_CONNECTION_ID = "connectionId";

    private static final AtomicLong CONNECTION_SEQUENCE = new AtomicLong();

    private static final String WELCOME = "Welcome to mud-server-java.\nType \"login <name>\" or \"register <name>\" to begin.\n";

    private final ExecutorService virtualThreadExecutor;
    private final ControllerDispatcher controllerDispatcher;
    private final AuthWorld authWorld;
    private final GameWorld gameWorld;

    public TelnetSessionHandler(ExecutorService virtualThreadExecutor, ControllerDispatcher controllerDispatcher,
            AuthWorld authWorld, GameWorld gameWorld) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.controllerDispatcher = controllerDispatcher;
        this.authWorld = authWorld;
        this.gameWorld = gameWorld;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String connectionId = "conn-" + CONNECTION_SEQUENCE.incrementAndGet();
        log.info("telnet.connection_opened remote={} connectionId={}", ctx.channel().remoteAddress(), connectionId);
        TelnetConnection connection = new TelnetConnection(connectionId, ctx.channel(), controllerDispatcher, authWorld,
                gameWorld);
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
        log.info("telnet.connection_closed remote={}", ctx.channel().remoteAddress());
        BlockingQueue<String> inbox = ctx.channel().attr(INBOX_KEY).get();
        if (inbox != null) {
            inbox.add(POISON_PILL);
        }
    }

    /**
     * Le MDC est posé ici, pas dans {@code channelActive}, car il vit dans un
     * {@code ThreadLocal} : il doit être peuplé sur le virtual thread qui exécute
     * réellement {@code handleLine}/les événements de domaine, pas sur le thread
     * I/O Netty qui appelle {@code channelActive}. Comme ce virtual thread est
     * dédié à cette connexion pour toute sa durée de vie (voir Javadoc de classe),
     * {@code connectionId} reste correct sur toutes les lignes de log émises tant
     * que la boucle tourne, sans propagation supplémentaire à gérer.
     */
    private void runConnectionLoop(TelnetConnection connection, BlockingQueue<String> inbox) {
        MDC.put(MDC_CONNECTION_ID, connection.getConnectionId());
        connection.write(WELCOME);
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

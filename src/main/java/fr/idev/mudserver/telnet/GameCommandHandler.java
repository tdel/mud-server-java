package fr.idev.mudserver.telnet;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;

/**
 * Frontière Netty <-> logique métier. Chaque connexion obtient exactement un virtual thread,
 * démarré à {@code channelActive} et vivant jusqu'à la déconnexion : c'est lui, et lui seul,
 * qui exécute {@code session.handleLine(...)}, en dépilant une file FIFO alimentée par
 * {@code channelRead0}. C'est le calque direct du "un coroutine par connexion" de Swoole
 * côté PHP (TelnetConnectionHandler::run()).
 *
 * <p>Point important : {@code channelRead0} ne doit jamais soumettre indépendamment chaque
 * ligne au pool de virtual threads (un {@code executor.execute()} par ligne) — deux lignes
 * reçues dans le même paquet TCP se traiteraient alors sur deux virtual threads distincts,
 * sans garantie d'ordre d'exécution entre eux (seul l'ordre d'appel de {@code channelRead0}
 * sur le thread NIO est garanti, pas l'ordre d'exécution des tâches une fois remises à
 * l'executor). D'où la file : un seul virtual thread consommateur par connexion.
 */
public class GameCommandHandler extends SimpleChannelInboundHandler<String> {

    private static final AttributeKey<TelnetSession> SESSION_KEY = AttributeKey.valueOf("telnetSession");
    private static final AttributeKey<BlockingQueue<String>> INBOX_KEY = AttributeKey.valueOf("telnetInbox");
    private static final String POISON_PILL = new String();

    private final ExecutorService virtualThreadExecutor;

    public GameCommandHandler(ExecutorService virtualThreadExecutor) {
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        TelnetSession session = new TelnetSession(ctx.channel());
        session.setLineHandler(line -> handleDemoLine(session, line));
        BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        ctx.channel().attr(SESSION_KEY).set(session);
        ctx.channel().attr(INBOX_KEY).set(inbox);
        virtualThreadExecutor.execute(() -> runConnectionLoop(session, inbox));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String line) {
        ctx.channel().attr(INBOX_KEY).get().add(line);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        BlockingQueue<String> inbox = ctx.channel().attr(INBOX_KEY).get();
        if (inbox != null) {
            inbox.add(POISON_PILL);
        }
    }

    private void runConnectionLoop(TelnetSession session, BlockingQueue<String> inbox) {
        session.write("Bienvenue sur mud-server-java (démo écho, tapez \"mask\" pour tester un prompt masqué).\r\n> ");
        try {
            while (true) {
                String line = inbox.take();
                if (line == POISON_PILL) {
                    return;
                }
                session.handleLine(line);
                session.write("> ");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleDemoLine(TelnetSession session, String line) {
        if ("mask".equalsIgnoreCase(line.trim())) {
            session.promptMasked("Mot de passe (masqué) : ", typed -> session.write("Tu as tapé : " + typed + "\r\n"));
        } else {
            session.write(line + "\r\n");
        }
    }
}

package fr.idev.mudserver.telnet;

import java.util.concurrent.ExecutorService;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Démarré sur {@link ApplicationReadyEvent} plutôt qu'en tant que commande séparée — c'est
 * l'équivalent direct de {@code app:telnet:serve} côté PHP, mais ici c'est le point d'entrée
 * principal de l'application, pas une sous-commande. Le listener bloque volontairement sur
 * le thread principal jusqu'à l'arrêt du serveur (mêmes effets que le {@code Co\run()}
 * bloquant du bootstrap Swoole).
 */
@Component
public class TelnetServer {

    private static final Logger log = LoggerFactory.getLogger(TelnetServer.class);

    private final ExecutorService virtualThreadExecutor;
    private final int port;

    public TelnetServer(ExecutorService virtualThreadExecutor, @Value("${app.telnet.port}") int port) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws InterruptedException {
        EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new TelnetServerInitializer(virtualThreadExecutor));

            Channel channel = bootstrap.bind(port).sync().channel();
            log.info("Serveur telnet démarré sur le port {}", port);
            channel.closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}

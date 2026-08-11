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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerDispatcher;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.WorldInstanceService;

@Component
@ConditionalOnProperty(prefix = "app.telnet", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TelnetServer {

    private static final Logger log = LoggerFactory.getLogger(TelnetServer.class);

    private final ExecutorService virtualThreadExecutor;
    private final ControllerDispatcher controllerDispatcher;
    private final AuthWorld authWorld;
    private final WorldInstanceService worldInstanceService;
    private final int port;

    public TelnetServer(ExecutorService virtualThreadExecutor, ControllerDispatcher controllerDispatcher,
            AuthWorld authWorld, WorldInstanceService worldInstanceService, @Value("${app.telnet.port}") int port) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.controllerDispatcher = controllerDispatcher;
        this.authWorld = authWorld;
        this.worldInstanceService = worldInstanceService;
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws InterruptedException {
        EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        try {
            ServerBootstrap bootstrap = new ServerBootstrap().group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class).childHandler(new TelnetServerInitializer(
                            virtualThreadExecutor, controllerDispatcher, authWorld, worldInstanceService));

            Channel channel = bootstrap.bind(port).sync().channel();
            log.info("Serveur telnet démarré sur le port {}", port);
            channel.closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}

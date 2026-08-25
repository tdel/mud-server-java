package app.network.server.tcpjson;

import java.util.concurrent.ExecutorService;

import tools.jackson.databind.ObjectMapper;

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

import app.network.CommandDispatcher;
import app.game.AuthWorld;
import app.game.WorldInstanceService;

@Component
@ConditionalOnProperty(prefix = "app.tcpjson", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TcpJsonServer {

    private static final Logger log = LoggerFactory.getLogger(TcpJsonServer.class);

    private final ExecutorService virtualThreadExecutor;
    private final ObjectMapper objectMapper;
    private final CommandDispatcher commandDispatcher;
    private final AuthWorld authWorld;
    private final WorldInstanceService worldInstanceService;
    private final int port;

    public TcpJsonServer(ExecutorService virtualThreadExecutor, ObjectMapper objectMapper,
            CommandDispatcher commandDispatcher, AuthWorld authWorld, WorldInstanceService worldInstanceService,
            @Value("${app.tcpjson.port}") int port) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.objectMapper = objectMapper;
        this.commandDispatcher = commandDispatcher;
        this.authWorld = authWorld;
        this.worldInstanceService = worldInstanceService;
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        // Ne bloque pas le thread de dispatch d'ApplicationReadyEvent, pour ne pas
        // retarder
        // les autres listeners de cet événement.
        virtualThreadExecutor.execute(this::runServer);
    }

    private void runServer() {
        EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        try {
            ServerBootstrap bootstrap = new ServerBootstrap().group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class).childHandler(new TcpJsonServerInitializer(
                            virtualThreadExecutor, objectMapper, commandDispatcher, authWorld, worldInstanceService));

            Channel channel = bootstrap.bind(port).sync().channel();
            log.info("Serveur TCP/JSON démarré sur le port {}", port);
            channel.closeFuture().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}

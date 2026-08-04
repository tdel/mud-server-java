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
import fr.idev.mudserver.game.ClassService;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.ItemService;
import fr.idev.mudserver.game.LevelService;
import fr.idev.mudserver.game.RaceService;
import fr.idev.mudserver.game.RoomService;

/**
 * Démarré sur {@link ApplicationReadyEvent} plutôt qu'en tant que commande
 * séparée — c'est l'équivalent direct de {@code app:telnet:serve} côté PHP,
 * mais ici c'est le point d'entrée principal de l'application, pas une
 * sous-commande. Le listener bloque volontairement sur le thread appelant
 * jusqu'à l'arrêt du serveur (mêmes effets que le {@code Co\run()} bloquant du
 * bootstrap Swoole) — {@code ApplicationReadyEvent} est publié de façon
 * synchrone, donc ce blocage empêche {@code SpringApplication.run()} de jamais
 * retourner. Sans le flag {@code app.telnet.enabled=false} (voir
 * {@code src/test/resources/application.yml}), n'importe quel
 * {@code @SpringBootTest} resterait bloqué indéfiniment au démarrage du
 * contexte.
 */
@Component
@ConditionalOnProperty(prefix = "app.telnet", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TelnetServer {

    private static final Logger log = LoggerFactory.getLogger(TelnetServer.class);

    private final ExecutorService virtualThreadExecutor;
    private final ControllerDispatcher controllerDispatcher;
    private final AuthWorld authWorld;
    private final GameWorld gameWorld;
    private final RoomService roomService;
    private final ItemService itemService;
    private final RaceService raceService;
    private final ClassService classService;
    private final LevelService levelService;
    private final int port;

    public TelnetServer(ExecutorService virtualThreadExecutor, ControllerDispatcher controllerDispatcher,
            AuthWorld authWorld, GameWorld gameWorld, RoomService roomService, ItemService itemService,
            RaceService raceService, ClassService classService, LevelService levelService,
            @Value("${app.telnet.port}") int port) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.controllerDispatcher = controllerDispatcher;
        this.authWorld = authWorld;
        this.gameWorld = gameWorld;
        this.roomService = roomService;
        this.itemService = itemService;
        this.raceService = raceService;
        this.classService = classService;
        this.levelService = levelService;
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws InterruptedException {
        roomService.warmRooms();
        itemService.warmItemTemplates();
        itemService.warmRoomItems(roomService.allRooms());
        raceService.warmRaceBonuses();
        classService.warmClassHitDice();
        levelService.warmXpThresholds();

        EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        try {
            ServerBootstrap bootstrap = new ServerBootstrap().group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class).childHandler(new TelnetServerInitializer(
                            virtualThreadExecutor, controllerDispatcher, authWorld, gameWorld));

            Channel channel = bootstrap.bind(port).sync().channel();
            log.info("Serveur telnet démarré sur le port {}", port);
            channel.closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}

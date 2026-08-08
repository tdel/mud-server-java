package fr.idev.mudserver.telnet;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

import fr.idev.mudserver.controller.ControllerDispatcher;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.network.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code ChannelInitializer<SocketChannel>#initChannel} n'est pas testable
 * directement via {@link EmbeddedChannel} : le compilateur génère une méthode
 * pont ({@code initChannel(Channel)} castant vers {@code SocketChannel}) à
 * cause du générique covariant sur {@link TelnetServerInitializer}, et
 * {@code EmbeddedChannel} n'est pas un {@code SocketChannel} — le cast lève une
 * {@code ClassCastException} silencieusement avalée par
 * {@code ChannelInitializer#exceptionCaught} (log + fermeture du canal), sans
 * qu'aucun handler ne soit jamais ajouté. Un vrai {@link NioSocketChannel},
 * enregistré sur un {@link EventLoopGroup} sans jamais être connecté à un
 * socket réel, contourne ce piège pour vérifier la forme du pipeline.
 */
class TelnetServerInitializerTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void initChannelBuildsThePipelineInTheExpectedOrder() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            NioSocketChannel channel = new NioSocketChannel();
            channel.pipeline()
                    .addLast(new TelnetServerInitializer(executor, noopDispatcher(), noopAuthWorld(), noopGameWorld()));
            group.register(channel).syncUninterruptibly();

            ChannelPipeline pipeline = channel.pipeline();
            assertThat(pipeline.names()).containsSubsequence(handlerName(pipeline, IacFilterDecoder.class),
                    handlerName(pipeline, DelimiterBasedFrameDecoder.class), handlerName(pipeline, StringDecoder.class),
                    handlerName(pipeline, StringEncoder.class), handlerName(pipeline, TelnetSessionHandler.class));

            channel.close();
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    void pipelineSplitsLinesOnCrlfDelimiterAndStripsIt() {
        EmbeddedChannel channel = new EmbeddedChannel(new IacFilterDecoder(),
                new DelimiterBasedFrameDecoder(1024, true, true, Delimiters.lineDelimiter()),
                new StringDecoder(CharsetUtil.UTF_8));

        channel.writeInbound(Unpooled.copiedBuffer("hello\r\n", StandardCharsets.UTF_8));

        String decoded = channel.readInbound();
        assertThat(decoded).isEqualTo("hello");
    }

    @Test
    void pipelineFailsFastOnALineExceeding1024CharactersWithoutADelimiter() {
        EmbeddedChannel channel = new EmbeddedChannel(new IacFilterDecoder(),
                new DelimiterBasedFrameDecoder(1024, true, true, Delimiters.lineDelimiter()),
                new StringDecoder(CharsetUtil.UTF_8));
        String tooLong = "a".repeat(1025);

        assertThatThrownBy(() -> channel.writeInbound(Unpooled.copiedBuffer(tooLong, StandardCharsets.UTF_8)))
                .isInstanceOf(TooLongFrameException.class);
    }

    private String handlerName(ChannelPipeline pipeline, Class<?> handlerType) {
        return pipeline.names().stream().filter(name -> handlerType.isInstance(pipeline.get(name))).findFirst()
                .orElseThrow(() -> new AssertionError(handlerType + " not found in pipeline: " + pipeline.names()));
    }

    private ControllerDispatcher noopDispatcher() {
        return new ControllerDispatcher(null, null) {
            @Override
            public void dispatch(Connection connection, String actionName, String argument) {
                // inutilisé : ce test vérifie la forme du pipeline, pas le traitement des
                // commandes
            }
        };
    }

    private AuthWorld noopAuthWorld() {
        return new AuthWorld(null, null);
    }

    private GameWorld noopGameWorld() {
        return new GameWorld(null, null, null, null, null, null);
    }
}

package app.network.server.tcpjson;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import tools.jackson.databind.ObjectMapper;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;

import app.network.CommandDispatcher;
import app.game.AuthWorld;
import app.game.WorldInstanceService;

public class TcpJsonServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final int MAX_LINE_LENGTH = 65536;
    // Silence client → Ping, puis encore ce délai sans réponse → fermeture : une
    // connexion morte est libérée en 60 s au plus.
    private static final int READER_IDLE_SECONDS = 30;
    // Octets en attente d'envoi : au-delà de HIGH le channel n'est plus
    // « writable » (rafale d'entrée en carte comprise, très en dessous).
    private static final int WRITE_BUFFER_LOW_WATER = 2 * 1024 * 1024;
    private static final int WRITE_BUFFER_HIGH_WATER = 8 * 1024 * 1024;

    private final ExecutorService virtualThreadExecutor;
    private final ObjectMapper objectMapper;
    private final CommandDispatcher commandDispatcher;
    private final AuthWorld authWorld;
    private final WorldInstanceService worldInstanceService;

    public TcpJsonServerInitializer(ExecutorService virtualThreadExecutor, ObjectMapper objectMapper,
            CommandDispatcher commandDispatcher, AuthWorld authWorld, WorldInstanceService worldInstanceService) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.objectMapper = objectMapper;
        this.commandDispatcher = commandDispatcher;
        this.authWorld = authWorld;
        this.worldInstanceService = worldInstanceService;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        // Tampon d'envoi borné : voir TcpJsonConnection.write, qui ferme la connexion
        // d'un client qui ne lit plus au lieu d'empiler ses messages en mémoire.
        ch.config().setWriteBufferWaterMark(new WriteBufferWaterMark(WRITE_BUFFER_LOW_WATER, WRITE_BUFFER_HIGH_WATER));
        ChannelPipeline pipeline = ch.pipeline();
        // Heartbeat (voir TcpJsonSessionHandler.userEventTriggered) : READER_IDLE
        // après ce délai sans aucun octet reçu du client.
        pipeline.addLast(new IdleStateHandler(READER_IDLE_SECONDS, 0, 0, TimeUnit.SECONDS));
        pipeline.addLast(new DelimiterBasedFrameDecoder(MAX_LINE_LENGTH, true, true, Delimiters.lineDelimiter()));
        pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
        pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
        pipeline.addLast(new TcpJsonSessionHandler(virtualThreadExecutor, objectMapper, commandDispatcher, authWorld,
                worldInstanceService));
    }
}

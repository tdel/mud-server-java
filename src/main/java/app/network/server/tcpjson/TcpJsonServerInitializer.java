package app.network.server.tcpjson;

import java.util.concurrent.ExecutorService;

import tools.jackson.databind.ObjectMapper;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

import app.network.CommandDispatcher;
import app.game.AuthWorld;
import app.game.WorldInstanceService;

public class TcpJsonServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final int MAX_LINE_LENGTH = 65536;

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
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new DelimiterBasedFrameDecoder(MAX_LINE_LENGTH, true, true, Delimiters.lineDelimiter()));
        pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
        pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
        pipeline.addLast(new TcpJsonSessionHandler(virtualThreadExecutor, objectMapper, commandDispatcher, authWorld,
                worldInstanceService));
    }
}

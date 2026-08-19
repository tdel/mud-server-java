package fr.idev.mudserver.network.server.tui;

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

import fr.idev.mudserver.controller.ControllerDispatcher;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.WorldInstanceService;

public class TuiServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final int MAX_LINE_LENGTH = 65536;

    private final ExecutorService virtualThreadExecutor;
    private final ObjectMapper objectMapper;
    private final ControllerDispatcher controllerDispatcher;
    private final AuthWorld authWorld;
    private final WorldInstanceService worldInstanceService;

    public TuiServerInitializer(ExecutorService virtualThreadExecutor, ObjectMapper objectMapper,
            ControllerDispatcher controllerDispatcher, AuthWorld authWorld, WorldInstanceService worldInstanceService) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.objectMapper = objectMapper;
        this.controllerDispatcher = controllerDispatcher;
        this.authWorld = authWorld;
        this.worldInstanceService = worldInstanceService;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new DelimiterBasedFrameDecoder(MAX_LINE_LENGTH, true, true, Delimiters.lineDelimiter()));
        pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
        pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
        pipeline.addLast(new TuiSessionHandler(virtualThreadExecutor, objectMapper, controllerDispatcher, authWorld,
                worldInstanceService));
    }
}

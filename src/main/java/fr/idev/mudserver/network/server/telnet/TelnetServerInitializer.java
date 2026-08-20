package fr.idev.mudserver.network.server.telnet;

import java.util.concurrent.ExecutorService;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

import fr.idev.mudserver.network.CommandDispatcher;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.WorldInstanceService;

public class TelnetServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final int MAX_LINE_LENGTH = 1024;

    private final ExecutorService virtualThreadExecutor;
    private final CommandDispatcher commandDispatcher;
    private final AuthWorld authWorld;
    private final WorldInstanceService worldInstanceService;

    public TelnetServerInitializer(ExecutorService virtualThreadExecutor, CommandDispatcher commandDispatcher,
            AuthWorld authWorld, WorldInstanceService worldInstanceService) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.commandDispatcher = commandDispatcher;
        this.authWorld = authWorld;
        this.worldInstanceService = worldInstanceService;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new IacFilterDecoder());
        pipeline.addLast(new DelimiterBasedFrameDecoder(MAX_LINE_LENGTH, true, true, Delimiters.lineDelimiter()));
        pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
        pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
        pipeline.addLast(
                new TelnetSessionHandler(virtualThreadExecutor, commandDispatcher, authWorld, worldInstanceService));
    }
}

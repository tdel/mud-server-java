package fr.idev.mudserver.telnet;

import java.util.concurrent.ExecutorService;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.network.ActionDispatcher;

public class TelnetServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final int MAX_LINE_LENGTH = 1024;

    private final ExecutorService virtualThreadExecutor;
    private final ActionDispatcher actionDispatcher;
    private final AuthWorld authWorld;
    private final GameWorld gameWorld;

    public TelnetServerInitializer(
            ExecutorService virtualThreadExecutor,
            ActionDispatcher actionDispatcher,
            AuthWorld authWorld,
            GameWorld gameWorld
    ) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.actionDispatcher = actionDispatcher;
        this.authWorld = authWorld;
        this.gameWorld = gameWorld;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new IacFilterDecoder());
        pipeline.addLast(new DelimiterBasedFrameDecoder(MAX_LINE_LENGTH, true, true, Delimiters.lineDelimiter()));
        pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
        pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
        pipeline.addLast(new GameCommandHandler(virtualThreadExecutor, actionDispatcher, authWorld, gameWorld));
    }
}

package net.unit8.wscl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;

import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Provide classes via WebSocket.
 *
 * @author kawasima
 */
public class ClassProvider {
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private final Map<UUID, ClassLoader> classLoaderHolder = new HashMap<UUID, ClassLoader>();
    private FindResourceHandler findResourceHandler;

    public ClassProvider() {
        findResourceHandler = new FindResourceHandler(classLoaderHolder);
    }
    public ServerBootstrap start(int port) {
        // TODO ssl support
        final SslContext sslCtx = null;

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        if (sslCtx != null) {
                            pipeline.addLast(sslCtx.newHandler(ch.alloc()));
                        }
                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new HttpObjectAggregator(65536));
                        pipeline.addLast(findResourceHandler);
                    }
                });

        bootstrap.bind(new InetSocketAddress(port));
        return bootstrap;
    }

    public UUID registerClasspath(URL[] urls) {
        UUID classLoaderId = UUID.randomUUID();
        classLoaderHolder.put(
                classLoaderId,
                new URLClassLoader(urls));

        return classLoaderId;
    }

    public void stop() {
        if (bossGroup != null)
            bossGroup.shutdownGracefully();
        if (workerGroup != null)
            workerGroup.shutdownGracefully();
    }

}

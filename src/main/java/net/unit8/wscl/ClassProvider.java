package net.unit8.wscl;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;

import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Provide classes via WebSocket.
 *
 * @author kawasima
 */
public class ClassProvider {
    private ServerBootstrap bootstrap;
    private final Map<UUID, ClassLoader> classLoaderHolder = new HashMap<UUID, ClassLoader>();
    private FindResourceHandler findResourceHandler;

    public ClassProvider() {
        findResourceHandler = new FindResourceHandler(classLoaderHolder);
    }
    public ServerBootstrap start(int port) {
        ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()
                )
        );
        bootstrap.setPipelineFactory(
                new ChannelPipelineFactory() {
                    @Override
                    public ChannelPipeline getPipeline() throws Exception {
                        ChannelPipeline pipeline =  Channels.pipeline();
                        pipeline.addLast("decoder", new HttpRequestDecoder());
                        pipeline.addLast("aggregator", new HttpChunkAggregator(65536));
                        pipeline.addLast("encoder", new HttpResponseEncoder());
                        pipeline.addLast("handler", findResourceHandler);
                        return pipeline;
                    }
                }
        );
        bootstrap.bind(new InetSocketAddress(port));
        this.bootstrap = bootstrap;
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
        if (bootstrap != null)
            bootstrap.shutdown();
    }

}

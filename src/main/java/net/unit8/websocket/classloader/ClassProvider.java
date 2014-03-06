package net.unit8.websocket.classloader;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Created by tie199026 on 14/03/06.
 */
public class ClassProvider {
    public void start(int port) {
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
                        pipeline.addLast("handler", new FindClassHandler());
                        return pipeline;
                    }
                }
        );
        bootstrap.bind(new InetSocketAddress(port));
    }

    public static void main(String[] args) {
        new ClassProvider().start(5000);
    }
}

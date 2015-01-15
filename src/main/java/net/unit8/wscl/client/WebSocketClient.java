package net.unit8.wscl.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashSet;

/**
 * @author kawasima
 */
public class WebSocketClient {
    private URI url;
    private EventLoopGroup group = new NioEventLoopGroup();
    private Channel channel;
    private static final Logger logger = LoggerFactory.getLogger(WebSocketClient.class);


    protected final Collection<WebSocketListener> listeners = new HashSet<WebSocketListener>();

    public WebSocketClient(String url) {
        try {
            this.url = new URI(url);
        } catch (URISyntaxException e)  {
            throw new IllegalArgumentException(e);
        }
    }

    public void connect() throws ConnectTimeoutException {
        connect(5000l);
    }

    public void connect(long timeout) throws ConnectTimeoutException {
        final WebSocketClientHandler handler =
                new WebSocketClientHandler(this,
                        WebSocketClientHandshakerFactory.newHandshaker(
                                url, WebSocketVersion.V13, null, false, new DefaultHttpHeaders()
                        )
                );
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        ChannelPipeline p = socketChannel.pipeline();
                        p.addLast(
                                new HttpClientCodec(),
                                new HttpObjectAggregator(8192),
                                handler);
                    }
                });
        ChannelFuture future = bootstrap.connect(url.getHost(), url.getPort());
        if (future.awaitUninterruptibly(timeout)) {
            channel = future.channel();
            logger.info("channel set" + channel);
            handler.handshakeFuture().syncUninterruptibly();

            for (WebSocketListener listener : listeners) {
                try {
                    listener.onOpen(this);
                } catch (Throwable t) {
                    listener.onError(t);
                }
            }
        } else {
            throw new ConnectTimeoutException("Connection timeout: " + timeout + "msec");
        }
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

    public void close() {
        if (channel.isOpen()) {
            onClose();
            listeners.clear();
            channel.writeAndFlush(new CloseWebSocketFrame()).addListener(ChannelFutureListener.CLOSE);
        }
    }
    public void onClose() {
        for(WebSocketListener listener : listeners) {
            listener.onClose(this);
        }
    }

    public String getHost() {
        return url.getHost();
    }

    public void onBinaryFrame(BinaryWebSocketFrame frame) {
        ByteBuf buf = frame.retain().content();
        logger.debug("onBinaryFrame: readableBytes=" + buf.readableBytes());
        if (buf != null && buf.readableBytes() > 0) {
            try {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                WebSocketListener[] listenerArray = listeners.toArray(new WebSocketListener[listeners.size()]);
                logger.debug("listeners=" + listeners);
                for (WebSocketListener listener : listenerArray) {
                    try {
                        logger.debug("onBinaryFrame Listener: " + listener);
                        if (listener instanceof WebSocketByteListener) {
                            ((WebSocketByteListener) listener).onMessage(bytes);
                        }
                    } catch (Throwable t) {
                        logger.error("listener error", t);
                        listener.onError(t);
                    }
                }
            } finally {
                buf.release();
            }
        }
    }

    public void onTextFrame(TextWebSocketFrame frame) {
        String msg = frame.retain().text();
        if (msg != null) {
            try {
                for (WebSocketListener listener : listeners) {
                    try {
                        if (listener instanceof  WebSocketTextListener) {
                            ((WebSocketTextListener) listener).onMessage(msg);
                        }
                    } catch (Throwable t) {
                        listener.onError(t);
                    }
                }
            } finally {
                frame.release();
            }
        }
    }

    public WebSocketClient sendMessage(String message) {
        channel.writeAndFlush(new TextWebSocketFrame(message));
        return this;
    }

    public WebSocketClient sendMessage(byte[] message) {
        channel.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(message)));
        return this;
    }

    public WebSocketClient addListener(WebSocketListener listener) {
        listeners.add(listener);
        return this;
    }

    public WebSocketClient removeListener(WebSocketListener listener) {
        listeners.remove(listener);
        return this;
    }
}

package net.unit8.wscl.client;

import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author kawasima
 */
public class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketClientHandler.class);
    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;
    private WebSocketClient client;

    public WebSocketClientHandler(WebSocketClient client, WebSocketClientHandshaker handshaker) {
        this.client = client;
        this.handshaker = handshaker;
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        handshaker.handshake(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.debug("WebSocket Client disconnected!");
        client.onClose();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            handshaker.finishHandshake(ch, (FullHttpResponse) msg);
            logger.debug("WebSocket Client connected!");
            handshakeFuture.setSuccess();
            return;
        }

        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException(
                    "Unexpected FullHttpResponse (getStatus=" + response.getStatus() +
                            ", content=" + response.content().toString(CharsetUtil.UTF_8) + ')');
        }

        WebSocketFrame frame = (WebSocketFrame) msg;
        if (frame instanceof TextWebSocketFrame) {
            logger.debug("Recv TextFrame:" + frame);
            client.onTextFrame((TextWebSocketFrame) frame);
        } else if (frame instanceof BinaryWebSocketFrame) {
            logger.debug("Recv BinaryFrame:" + frame);
            client.onBinaryFrame((BinaryWebSocketFrame) frame);
        } else if (frame instanceof PongWebSocketFrame) {
            logger.debug("WebSocket Client received pong");
        } else if (frame instanceof CloseWebSocketFrame) {
            logger.debug("WebSocket Client received closing");
            client.onClose();
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        ctx.close();
    }
}

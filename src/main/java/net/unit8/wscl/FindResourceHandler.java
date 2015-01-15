package net.unit8.wscl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import net.unit8.wscl.dto.ResourceRequest;
import net.unit8.wscl.dto.ResourceResponse;
import net.unit8.wscl.handler.ResourceRequestReadHandler;
import net.unit8.wscl.handler.ResourceResponseWriteHandler;
import net.unit8.wscl.util.DigestUtils;
import net.unit8.wscl.util.FressianUtils;
import net.unit8.wscl.util.IOUtils;
import org.fressian.FressianReader;
import org.fressian.FressianWriter;
import org.fressian.handlers.ILookup;
import org.fressian.handlers.ReadHandler;
import org.fressian.handlers.WriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.UUID;


/**
 * Find a requested class, and send it to client.
 *
 * @author kawasima
 */
public class FindResourceHandler extends SimpleChannelInboundHandler<Object> {
    private static Logger logger = LoggerFactory.getLogger(FindResourceHandler.class);
    private final Map<UUID, ClassLoader> classLoaderHolder;
    private WebSocketServerHandshaker handshaker;

    public FindResourceHandler(Map<UUID, ClassLoader> classLoaderHolder) {
        this.classLoaderHolder = classLoaderHolder;
    }
    public ClassLoader findClassLoader(UUID classLoaderId) {
        ClassLoader loader = null;
        if (classLoaderId != null) {
            loader = classLoaderHolder.get(classLoaderId);
        }
        return loader != null ? loader : Thread.currentThread().getContextClassLoader();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (!req.getDecoderResult().isSuccess()) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        if (req.getMethod() != HttpMethod.GET) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN));
            return;
        }

        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                getWebSocketLocation(req), null, true);
        handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), req);
        }
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        if (frame instanceof BinaryWebSocketFrame) {
            onBinaryWebSocketFrame(ctx, (BinaryWebSocketFrame) frame);
        } else {
            throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass().getName()));
        }
    }

    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
        if (res.getStatus() != HttpResponseStatus.OK) {
            ByteBuf buf = Unpooled.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
            HttpHeaders.setContentLength(res, res.content().readableBytes());
        }

        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!HttpHeaders.isKeepAlive(req) || res.getStatus() != HttpResponseStatus.OK) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }



    private void onBinaryWebSocketFrame(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
        logger.debug("onBinaryWebSocketFrame: " + frame);
        ResourceRequest req = null;
        try {
            ByteBufInputStream bbis = new ByteBufInputStream(frame.content().retain());
            FressianReader fr = new FressianReader(bbis, new ILookup<Object, ReadHandler>() {
                @Override
                public ReadHandler valAt(Object key) {
                    if (key.equals(ResourceRequest.class.getName()))
                        return new ResourceRequestReadHandler();
                    else
                        return null;
                }
            });
            req = (ResourceRequest) fr.readObject();
        } catch (IOException ex) {
            logger.warn("Client connection is invalid. disconnect " + ctx, ex);
        }
        if (req == null) {
            ctx.disconnect();
            return;
        }
        logger.debug("classLoaderId=" + req.getClassLoaderId() +
                ", resourceName=" + req.getResourceName());
        ClassLoader cl = findClassLoader(req.getClassLoaderId());
        URL url = cl.getResource(req.getResourceName());
        ResourceResponse res = new ResourceResponse(req.getResourceName());

        ByteArrayOutputStream baos = new ByteArrayOutputStream(65535);
        try {
            if (url != null) {
                byte[] classBytes = IOUtils.slurp(url);
                res.setDigest(DigestUtils.md5hash(classBytes));
                if (!req.isCheckOnly()) {
                    res.setBytes(classBytes);
                }
            }
            FressianWriter fw = new FressianWriter(baos, new ILookup<Class, Map<String, WriteHandler>>() {
                @Override
                public Map<String, WriteHandler> valAt(Class key) {
                    if (key.equals(ResourceResponse.class)) {
                        return FressianUtils.map(
                                ResourceResponse.class.getName(),
                                new ResourceResponseWriteHandler());
                    } else {
                        return null;
                    }
                }
            });
            fw.writeObject(res);
            fw.close();
        } catch (IOException ex) {
            logger.warn("Client connection is invalid. disconnect " + ctx, ex);
        } finally {
            IOUtils.closeQuietly(baos);
        }

        WebSocketFrame outFrame = new BinaryWebSocketFrame(Unpooled.copiedBuffer(baos.toByteArray()));

        logger.debug("write frame for " + res.getResourceName() + ": "+ outFrame);
        ctx.writeAndFlush(outFrame);
    }

    private static String getWebSocketLocation(HttpRequest req) {
        return "ws://" + req.headers().get(HttpHeaders.Names.HOST);
    }

}

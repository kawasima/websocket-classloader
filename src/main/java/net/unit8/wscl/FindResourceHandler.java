package net.unit8.wscl;

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
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.websocketx.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Map;


/**
 * Find a requested class, and send it to client.
 *
 * @author kawasima
 */
public class FindResourceHandler extends SimpleChannelUpstreamHandler {
    private static Logger logger = LoggerFactory.getLogger(FindResourceHandler.class);
    private WebSocketServerHandshaker handshaker;
    private ClassLoader loader;

    public void setClassLoader(ClassLoader loader) {
        this.loader = loader;
    }
    @Override
    public void messageReceived(
            ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        } if (msg instanceof HttpRequest) {
            handleHttpRequest(ctx, (HttpRequest) msg);
        }
    }

    public void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest req) {
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                getWebSocketLocation(req), null, false);
        handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null) {
            wsFactory.sendUnsupportedWebSocketVersionResponse(ctx.getChannel());
        } else {
            handshaker.handshake(ctx.getChannel(), req).addListener(WebSocketServerHandshaker.HANDSHAKE_LISTENER);
        }
    }

    private void onBinaryWebSocketFrame(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
        ResourceRequest req = null;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(frame.getBinaryData().array());
            FressianReader fr = new FressianReader(bais, new ILookup<Object, ReadHandler>() {
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
            logger.warn("Client connection is invalid. disconnect " + ctx.getChannel(), ex);
        }
        if (req == null) {
            ctx.getChannel().disconnect();
            return;
        }

        ClassLoader cl = loader == null ? Thread.currentThread().getContextClassLoader() : loader;
        URL url = cl.getResource(req.getResourceName());
        ResourceResponse res = new ResourceResponse(req.getResourceName());

        ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
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
                    return FressianUtils.map(
                            ResourceResponse.class.getName(),
                            new ResourceResponseWriteHandler());
                }
            });
            fw.writeObject(res);
        } catch (IOException ex) {
            logger.warn("Client connection is invalid. disconnect " + ctx.getChannel(), ex);
        } finally {
            IOUtils.closeQuietly(baos);
        }

        WebSocketFrame outFrame = new BinaryWebSocketFrame(
                ChannelBuffers.copiedBuffer(baos.toByteArray()));
        ctx.getChannel().write(outFrame);
    }

    public void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.getChannel(), (CloseWebSocketFrame) frame);
        } else if (frame instanceof BinaryWebSocketFrame) {
            onBinaryWebSocketFrame(ctx, (BinaryWebSocketFrame) frame);
        }
    }

    private static String getWebSocketLocation(HttpRequest req) {
        return "ws://" + req.headers().get(HttpHeaders.Names.HOST);
    }

}

package net.unit8.wscl;

import net.unit8.wscl.handler.ClassRequestReadHandler;
import net.unit8.wscl.handler.ClassResponseWriteHandler;
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
import java.io.InputStream;
import java.net.URL;
import java.util.Map;


/**
 * Find a requested class, and send it to client.
 *
 * @author kawasima
 */
public class FindClassHandler extends SimpleChannelUpstreamHandler {
    private static Logger logger = LoggerFactory.getLogger(FindClassHandler.class);
    private WebSocketServerHandshaker handshaker;

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

    private byte[] toByteArray(InputStream in) throws IOException {
        int n;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(16384);
        byte[] data = new byte[4096];
        while((n = in.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();
    }

    private void onBinaryWebSocketFrame(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
        InputStream in = null;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(frame.getBinaryData().array());
            FressianReader fr = new FressianReader(bais, new ILookup<Object, ReadHandler>() {
                @Override
                public ReadHandler valAt(Object key) {
                    if (key.equals(ClassRequest.class.getName()))
                        return new ClassRequestReadHandler();
                    else
                        return null;
                }
            });
            ClassRequest req = (ClassRequest) fr.readObject();

            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            URL url = cl.getResource(req.getClassName().replace('.', '/') + ".class");
            ClassResponse res = new ClassResponse(req.getClassName());
            if (url != null) {
                in = url.openStream();
                res.setBytes(toByteArray(in));
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
            FressianWriter fw = new FressianWriter(baos, new ILookup<Class, Map<String, WriteHandler>>() {
                @Override
                public Map<String, WriteHandler> valAt(Class key) {
                    return FressianUtils.map(ClassResponse.class.getName(),
                            new ClassResponseWriteHandler());
                }
            });
            fw.writeObject(res);

            WebSocketFrame outFrame = new BinaryWebSocketFrame();
            outFrame.setBinaryData(ChannelBuffers.copiedBuffer(baos.toByteArray()));
            ctx.getChannel().write(outFrame);
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null)
                    in.close();
            } catch (IOException ignore) {

            }
        }
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

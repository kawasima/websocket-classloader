package net.unit8.websocket.classloader;

import org.fressian.FressianReader;
import org.fressian.FressianWriter;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketFrame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Created by tie199026 on 14/03/06.
 */
public class FindClassHandler extends SimpleChannelUpstreamHandler {
    @Override
    public void messageReceived(
            ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    private byte[] toByteArray(InputStream in) throws IOException {
        int n = 0;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(16384);
        byte[] data = new byte[4096];
        while((n = in.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();
    }

    public void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        InputStream in = null;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(frame.getBinaryData().array());
            FressianReader fr = new FressianReader(bais);
            ClassRequest req = (ClassRequest) fr.readObject();

            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            URL url = cl.getResource(req.className.replace('.', '/') + ".class");
            in = url.openStream();
            ClassResponse res = new ClassResponse(req.className, toByteArray(in));


            ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
            FressianWriter fw = new FressianWriter(baos);
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
}

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
import org.fressian.impl.ByteBufferInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Provide classes via WebSocket.
 *
 * @author kawasima
 */
@ServerEndpoint("/")
public class ClassProvider {
    private static final Logger logger = LoggerFactory.getLogger(ClassProvider.class);

    @OnOpen
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        logger.debug("client " + session + " connected");
    }

    private ClassLoader findClassLoader(UUID classLoaderId) {
        ClassLoader loader = null;
        if (classLoaderId != null) {
            loader = ClassLoaderHolder.getInstance().get(classLoaderId);
        }
        return loader != null ? loader : Thread.currentThread().getContextClassLoader();
    }

    @OnMessage
    public void findResource(ByteBuffer msg, Session session) {
        ResourceRequest req = null;
        try (InputStream is = new ByteBufferInputStream(msg)) {
            FressianReader fr = new FressianReader(is,
                    key -> key.equals(ResourceRequest.class.getName()) ? new ResourceRequestReadHandler() : null);
            req = (ResourceRequest) fr.readObject();
        } catch (IOException ignored) {

        }

        if (req == null) {
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, ""));
            } catch(IOException ignore) {
            }
            return;
        }

        ClassLoader cl = findClassLoader(req.getClassLoaderId());
        URL url = cl.getResource(req.getResourceName());
        ResourceResponse res = new ResourceResponse(req.getResourceName());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (url != null) {
                byte[] classBytes = IOUtils.slurp(url);
                res.setDigest(DigestUtils.md5hash(classBytes));
                if (!req.isCheckOnly()) {
                    res.setBytes(classBytes);
                }
            }
            logger.debug("findResource:" + req.getResourceName() + "(" + req.getClassLoaderId() + "):resourceUrl=" + url);

            FressianWriter fw = new FressianWriter(baos,
                    key -> key.equals(ResourceResponse.class)
                            ? FressianUtils.map(ResourceResponse.class.getName(), new ResourceResponseWriteHandler())
                            : null);
            fw.writeObject(res);
            fw.close();

            logger.debug("findResource:sendBinary=" + baos.toByteArray().length);
            session.getAsyncRemote().sendBinary(ByteBuffer.wrap(baos.toByteArray()), result -> {
                if (!result.isOK()) {
                    logger.warn("fail to sendBinary.", result.getException());
                }
            });
        } catch (IOException ex) {
            logger.warn("Client connection is invalid. disconnect " + session, ex);
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        logger.debug("Client " + session + " closed for" + closeReason);
    }

}

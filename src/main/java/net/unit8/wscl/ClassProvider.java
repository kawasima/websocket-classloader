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
import org.fressian.impl.ByteBufferInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Map;
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
            FressianReader fr = new FressianReader(is, new ILookup<Object, ReadHandler>() {
                @Override
                public ReadHandler valAt(Object key) {
                    if (key.equals(ResourceRequest.class.getName()))
                        return new ResourceRequestReadHandler();
                    else
                        return null;
                }
            });
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

        BinaryFrameOutputStream bfos = null;
        try {
            if (url != null) {
                byte[] classBytes = IOUtils.slurp(url);
                res.setDigest(DigestUtils.md5hash(classBytes));
                if (!req.isCheckOnly()) {
                    res.setBytes(classBytes);
                }
            }
            bfos = new BinaryFrameOutputStream(session.getAsyncRemote());
            FressianWriter fw = new FressianWriter(bfos, new ILookup<Class, Map<String, WriteHandler>>() {
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
            logger.warn("Client connection is invalid. disconnect " + session, ex);
        } finally {
            IOUtils.closeQuietly(bfos);
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        logger.debug("Client " + session + " closed for" + closeReason);
    }

}

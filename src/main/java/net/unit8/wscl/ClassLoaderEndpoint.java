package net.unit8.wscl;

import net.unit8.wscl.dto.ResourceRequest;
import net.unit8.wscl.dto.ResourceResponse;
import net.unit8.wscl.handler.ResourceRequestWriteHandler;
import net.unit8.wscl.handler.ResourceResponseReadHandler;
import net.unit8.wscl.util.FressianUtils;
import net.unit8.wscl.util.PropertyUtils;
import org.fressian.FressianReader;
import org.fressian.FressianWriter;
import org.fressian.impl.ByteBufferInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.websocket.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.*;

/**
 * @author kawasima
 */
@ClientEndpoint
public class ClassLoaderEndpoint extends Endpoint {
    private static final Logger logger = LoggerFactory.getLogger(ClassLoaderEndpoint.class);

    private volatile Session session;
    private final ConcurrentMap<String, BlockingQueue<ResourceResponse>> waitingResponses = new ConcurrentHashMap<>();

    public ClassLoaderEndpoint() {
    }

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        this.session = session;
        session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer buf) {
                try {
                    FressianReader reader = new FressianReader(new ByteBufferInputStream(buf),
                            key -> key.equals(ResourceResponse.class.getName()) ? new ResourceResponseReadHandler() : null);

                    Object obj = reader.readObject();
                    if (obj instanceof ResourceResponse response) {
                        BlockingQueue<ResourceResponse> queue = waitingResponses.computeIfAbsent(
                                response.getResourceName(), k -> new ArrayBlockingQueue<>(10));
                        queue.offer(response);
                    } else {
                        logger.warn("Fressian read response: " + obj + "(" + obj.getClass() + ")");
                    }
                } catch (IOException ex) {
                    logger.warn("read response error", ex);
                }
            }
        });
    }

    @SuppressWarnings("resource")
    public ResourceResponse request(ResourceRequest request) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FressianWriter fw = new FressianWriter(baos,
                key -> key.equals(ResourceRequest.class)
                        ? FressianUtils.map(ResourceRequest.class.getName(), new ResourceRequestWriteHandler())
                        : null);
        fw.writeObject(request);

        logger.debug("fetch class:" + request.getResourceName() + ":" + request.getClassLoaderId());

        BlockingQueue<ResourceResponse> queue = waitingResponses.computeIfAbsent(
                request.getResourceName(),
                k -> new ArrayBlockingQueue<>(
                        PropertyUtils.getLongSystemProperty("wscl.queue.size", 10).intValue()));

        try {
            session.getAsyncRemote().sendBinary(ByteBuffer.wrap(baos.toByteArray()));
            ResourceResponse response = queue.poll(
                    PropertyUtils.getLongSystemProperty("wscl.timeout", 5000),
                    TimeUnit.MILLISECONDS);

            if (response == null)
                throw new IOException("WebSocket request error." + request.getResourceName());
            return response;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted in waiting for request." + request.getResourceName(), ex);
        } finally {
            if (queue.isEmpty()) {
                waitingResponses.remove(request.getResourceName(), queue);
            }
        }
    }

    public void close() throws IOException {
        Session s = session;
        if (s != null) {
            session = null;
            if (s.isOpen()) {
                s.close();
            }
        }
    }
}

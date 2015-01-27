package net.unit8.wscl;

import net.unit8.wscl.dto.ResourceRequest;
import net.unit8.wscl.dto.ResourceResponse;
import net.unit8.wscl.handler.ResourceRequestWriteHandler;
import net.unit8.wscl.handler.ResourceResponseReadHandler;
import net.unit8.wscl.util.FressianUtils;
import net.unit8.wscl.util.IOUtils;
import net.unit8.wscl.util.PropertyUtils;
import net.unit8.wscl.util.QueryStringDecoder;
import org.fressian.FressianReader;
import org.fressian.FressianWriter;
import org.fressian.handlers.ILookup;
import org.fressian.handlers.ReadHandler;
import org.fressian.handlers.WriteHandler;
import org.fressian.impl.ByteBufferInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.MessageHandler;
import javax.websocket.Session;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author kawasima
 */
public class WebSocketURLConnection extends URLConnection {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketURLConnection.class);
    private final Session session;
    private final File cacheDirectory;
    private UUID classLoaderId;

    public WebSocketURLConnection(URL url, Session session, File cacheDirectory) {
        super(url);
        this.session = session;
        this.cacheDirectory = cacheDirectory;
        String query = url.getQuery();
        if (query != null) {
            List<String> classLoaderIds = new QueryStringDecoder(query).parameters().get("classLoaderId");
            if (!classLoaderIds.isEmpty()) {
                this.classLoaderId = UUID.fromString(classLoaderIds.get(0));
            }
        }
    }

    @Override
    public void connect() {
        // Do nothing.
    }

    public ResourceResponse doRequest(ResourceRequest request) throws IOException {
        ResourceReceiveListener listener;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (classLoaderId != null) {
            request.setClassLoaderId(classLoaderId);
        }
        FressianWriter fw = new FressianWriter(baos, new ILookup<Class, Map<String, WriteHandler>>() {
            @Override
            public Map<String, WriteHandler> valAt(Class key) {
                if (key.equals(ResourceRequest.class)) {
                    return FressianUtils.map(ResourceRequest.class.getName(),
                            new ResourceRequestWriteHandler());
                } else {
                    return null;
                }
            }
        });
        fw.writeObject(request);
        listener = new ResourceReceiveListener(request.getResourceName());
        session.addMessageHandler(listener);

        try {
            logger.debug("fetch class:" + request.getResourceName());
            session.getAsyncRemote().sendBinary(ByteBuffer.wrap(baos.toByteArray()));
            ResourceResponse response = listener.queue.poll(
                    PropertyUtils.getLongSystemProperty("wscl.timeout", 5000),
                    TimeUnit.MILLISECONDS);
            if (response == null)
                throw new IOException("Websocket request error." + request.getResourceName());
            if (cacheDirectory != null && !request.isCheckOnly()) {
                IOUtils.spitQuietly(
                        new File(cacheDirectory, url.getPath()),
                        response.getBytes());
            }
            return response;
        } catch (InterruptedException ex) {
            throw new IOException(ex);
        } finally {
            session.removeMessageHandler(listener);
        }
    }

    protected byte[] getResourceDigest() throws IOException {
        String resourcePath = getURL().getPath();
        ResourceResponse response = doRequest(new ResourceRequest(resourcePath, true));
        return response.getDigest();
    }

    @Override
    public InputStream getInputStream() {
        if (!"ws".equalsIgnoreCase(getURL().getProtocol())) {
            try {
                return getURL().openStream();
            } catch (IOException ex) {
                return null;
            }
        }
        String resourcePath = getURL().getPath();
        try {
            ResourceResponse response = doRequest(new ResourceRequest(resourcePath));
            return new ByteArrayInputStream(response.getBytes());
        } catch (IOException ex) {
            logger.debug("Can't retrieve resources.", ex);
            return null;
        }
    }

    @Override
    public Object getContent() {
        if (!"ws".equalsIgnoreCase(getURL().getProtocol())) {
            return IOUtils.slurpQuietly(getURL());
        }
        String resourcePath = getURL().getPath();
        try {
            ResourceResponse response = doRequest(new ResourceRequest(resourcePath));
            return response.getBytes();
        } catch (IOException ex) {
            logger.debug("Can't retrieve resources.", ex);
            return null;
        }
    }

    static class ResourceReceiveListener implements MessageHandler.Whole<ByteBuffer> {
        private final BlockingQueue<ResourceResponse> queue;
        private final String resourcePath;

        public ResourceReceiveListener(String resourcePath) {
            this.resourcePath = resourcePath;
            queue = new ArrayBlockingQueue<>(1);
        }

        @Override
        public void onMessage(ByteBuffer buf) {
            try {
                FressianReader reader = new FressianReader(new ByteBufferInputStream(buf), new ILookup<Object, ReadHandler>() {
                    @Override
                    public ReadHandler valAt(Object key) {
                        if (key.equals(ResourceResponse.class.getName()))
                            return new ResourceResponseReadHandler();
                        else
                            return null;
                    }
                });

                Object obj = reader.readObject();
                if (obj instanceof ResourceResponse) {
                    ResourceResponse response = (ResourceResponse) obj;
                    if (response.getResourceName().equals(resourcePath)) {
                        queue.put(response);
                    }
                } else {
                    logger.warn("Fressian read response: " + obj + "(" + obj.getClass() + ")");
                }
            } catch (IOException ex) {
                logger.warn("read response error", ex);
            } catch (InterruptedException ex) {
                logger.warn("interrupt error", ex);
            }
        }


        @Override
        public String toString() {
            return "ResourceReceiveListener(" + resourcePath + ")";
        }
    }
}

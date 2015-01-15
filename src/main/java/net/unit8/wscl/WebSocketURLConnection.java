package net.unit8.wscl;

import io.netty.handler.codec.http.QueryStringDecoder;
import net.unit8.wscl.client.WebSocketByteListener;
import net.unit8.wscl.client.WebSocketClient;
import net.unit8.wscl.dto.ResourceRequest;
import net.unit8.wscl.dto.ResourceResponse;
import net.unit8.wscl.handler.ResourceRequestWriteHandler;
import net.unit8.wscl.handler.ResourceResponseReadHandler;
import net.unit8.wscl.util.FressianUtils;
import net.unit8.wscl.util.IOUtils;
import net.unit8.wscl.util.PropertyUtils;
import org.fressian.FressianReader;
import org.fressian.FressianWriter;
import org.fressian.handlers.ILookup;
import org.fressian.handlers.ReadHandler;
import org.fressian.handlers.WriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
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
    private static Logger logger = LoggerFactory.getLogger(WebSocketURLConnection.class);
    private WebSocketClient client;
    private File cacheDirectory;
    private UUID classLoaderId;

    public WebSocketURLConnection(URL url, WebSocketClient client, File cacheDirectory) {
        super(url);
        this.client = client;
        this.cacheDirectory = cacheDirectory;
        String query = url.getQuery();
        if (query != null) {
            try {
                QueryStringDecoder decoder = new QueryStringDecoder(url.toURI());
                List<String> classLoaderIds = decoder.parameters().get("classLoaderId");
                if (classLoaderIds != null && !classLoaderIds.isEmpty())
                    this.classLoaderId = UUID.fromString(classLoaderIds.get(0));
            } catch (URISyntaxException ignore) {
                // ignore
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
        client.addListener(listener);

        try {
            logger.debug("fetch class:" + request.getResourceName());
            client.sendMessage(baos.toByteArray());
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
            client.removeListener(listener);
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

    static class ResourceReceiveListener extends WebSocketByteListener {
        private BlockingQueue<ResourceResponse> queue;
        private String resourcePath;

        public ResourceReceiveListener(String resourcePath) {
            this.resourcePath = resourcePath;
            queue = new ArrayBlockingQueue<ResourceResponse>(1);
        }

        @Override
        public void onMessage(byte[] bytes) {
            try {
                logger.debug("onMessage: fetched class:" + resourcePath);
                FressianReader reader = new FressianReader(new ByteArrayInputStream(bytes), new ILookup<Object, ReadHandler>() {
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
                    logger.debug("onMessage: " + response.getResourceName() + ":" + resourcePath);
                    if (response.getResourceName().equals(resourcePath)) {
                        queue.put(response);
                    }
                } else {
                    logger.debug("Fressian read response: " + obj + "(" + obj.getClass() +")");
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

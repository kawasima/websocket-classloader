package net.unit8.wscl;

import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketByteListener;
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
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author kawasima
 */
public class WebSocketURLConnection extends URLConnection {
    private static Logger logger = LoggerFactory.getLogger(WebSocketURLConnection.class);
    private WebSocket websocket;
    private File cacheDirectory;


    public WebSocketURLConnection(URL url, WebSocket websocket, File cacheDirectory) {
        super(url);
        this.websocket = websocket;
        this.cacheDirectory = cacheDirectory;
    }

    @Override
    public void connect() {
        // Do nothing.
    }

    public ResourceResponse doRequest(ResourceRequest request) throws IOException {
        ResourceReceiveListener listener;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FressianWriter fw = new FressianWriter(baos, new ILookup<Class, Map<String, WriteHandler>>() {
            @Override
            public Map<String, WriteHandler> valAt(Class key) {
                return FressianUtils.map(ResourceRequest.class.getName(),
                        new ResourceRequestWriteHandler());
            }
        });
        fw.writeObject(request);
        listener = new ResourceReceiveListener(request.getResourceName());
        websocket.addWebSocketListener(listener);

        try {
            logger.debug("fetch class:" + request.getResourceName());
            websocket.sendMessage(baos.toByteArray());
            ResourceResponse response = listener.queue.poll(
                    PropertyUtils.getLongSystemProperty("wscl.timeout", 5000),
                    TimeUnit.MILLISECONDS);
            if (response == null)
                throw new IOException("Websocket request error.");
            if (cacheDirectory != null && !request.isCheckOnly()) {
                IOUtils.spitQuietly(
                        new File(cacheDirectory, url.getPath()),
                        response.getBytes());
            }
            return response;
        } catch (InterruptedException ex) {
            throw new IOException(ex);
        } finally {
            websocket.removeWebSocketListener(listener);
        }
    }

    protected byte[] getResourceDigest() throws IOException {
        String resourcePath = getURL().getFile();
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
        } catch(IOException ex) {
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
        } catch(IOException ex) {
            logger.debug("Can't retrieve resources.", ex);
            return null;
        }
    }

    static class  ResourceReceiveListener implements WebSocketByteListener {
        private BlockingQueue<ResourceResponse> queue;
        private String resourcePath;

        public ResourceReceiveListener(String resourcePath) {
            this.resourcePath = resourcePath;
            queue = new ArrayBlockingQueue<ResourceResponse>(1);
        }

        @Override
        public void onMessage(byte[] bytes) {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            try {
                logger.debug("fetched class:" + resourcePath);
                ResourceResponse response = (ResourceResponse) new FressianReader(bais, new ILookup<Object, ReadHandler>() {
                    @Override
                    public ReadHandler valAt(Object key) {
                        if (key.equals(ResourceResponse.class.getName()))
                            return new ResourceResponseReadHandler();
                        else
                            return null;
                    }
                }).readObject();
                if (response.getResourceName().equals(resourcePath)) {
                    queue.put(response);
                }
            } catch (IOException ex) {
                logger.warn("read response error", ex);
            } catch (InterruptedException ex) {
                logger.warn("interrupt error", ex);
            }

        }

        @Override
        public void onFragment(byte[] fragment, boolean last) {
        }

        @Override
        public void onOpen(WebSocket websocket) {

        }

        @Override
        public void onClose(WebSocket websocket) {

        }

        @Override
        public void onError(Throwable t) {

        }
    }
}

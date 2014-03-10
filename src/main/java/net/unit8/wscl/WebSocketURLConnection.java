package net.unit8.wscl;

import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketByteListener;
import net.unit8.wscl.dto.ResourceRequest;
import net.unit8.wscl.dto.ResourceResponse;
import net.unit8.wscl.handler.ResourceRequestWriteHandler;
import net.unit8.wscl.handler.ResourceResponseReadHandler;
import org.fressian.FressianReader;
import org.fressian.FressianWriter;
import org.fressian.handlers.ILookup;
import org.fressian.handlers.ReadHandler;
import org.fressian.handlers.WriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * @author kawasima
 */
public class WebSocketURLConnection extends URLConnection {
    private static Logger logger = LoggerFactory.getLogger(WebSocketURLConnection.class);
    private WebSocket websocket;

    public WebSocketURLConnection(URL url, WebSocket websocket) {
        super(url);
        this.websocket = websocket;
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
        try {
            fw.writeObject(request);
            listener = new ResourceReceiveListener(request.getResourceName());
            websocket.addWebSocketListener(listener);
        } catch(IOException ex) {
            throw new RuntimeException(ex);
        }

        try {
            logger.debug("fetch class:" + request.getResourceName());
            websocket.sendMessage(baos.toByteArray());
            return listener.queue.take();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            websocket.removeWebSocketListener(listener);
        }
    }

    public boolean existsUrl() throws IOException {
        String resourcePath = getURL().getFile();
        ResourceResponse response = doRequest(new ResourceRequest(resourcePath, true));
        return response.exists();
    }

    @Override
    public InputStream getInputStream() {
        String resourcePath = getURL().getFile();
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
        String resourcePath = getURL().getFile();
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
            } catch (Exception ex) {
                ex.printStackTrace();
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

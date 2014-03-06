package net.unit8.websocket.classloader;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketByteListener;
import com.ning.http.client.websocket.WebSocketUpgradeHandler;
import org.fressian.FressianReader;
import org.fressian.FressianWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by tie199026 on 14/03/06.
 */
public class WebSocketClassLoader extends ClassLoader {
    private WebSocket websocket;
    private static Logger logger = LoggerFactory.getLogger(WebSocketClassLoader.class);

    public WebSocketClassLoader(ClassLoader parent, String url) {
        super(parent);
        AsyncHttpClient client = new AsyncHttpClient();
        try {
            websocket = client.prepareGet(url)
                    .execute(new WebSocketUpgradeHandler.Builder().addWebSocketListener(
                            new WebSocketByteListener() {
                                @Override
                                public void onMessage(byte[] bytes) {
                                }

                                @Override
                                public void onFragment(byte[] bytes, boolean b) {

                                }

                                @Override
                                public void onOpen(WebSocket webSocket) {
                                    logger.debug("Connected! to class provider.");
                                }

                                @Override
                                public void onClose(WebSocket webSocket) {

                                }

                                @Override
                                public void onError(Throwable throwable) {
                                }
                            }
                    ).build()).get();
        } catch (Exception ex) {

        }
    }

    public Class<?> loadClass(String className, boolean resolve)
            throws ClassNotFoundException {
        findLoadedClass(className);
        Class<?> clazz = findLoadedClass(className);
        if (clazz != null) {
            return clazz;
        }
        return defineClass(className, resolve);
    }

    private Class<?> defineClass(String className, boolean resolve) {
        ClassReceiveListener listener;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FressianWriter fw = new FressianWriter(baos);
        try {
            fw.writeObject(new ClassRequest(className));
            listener = new ClassReceiveListener(className);
            websocket.addWebSocketListener(listener);
        } catch(IOException ex) {
            throw new RuntimeException(ex);
        }

        try {
            websocket.sendMessage(baos.toByteArray());
            byte[] bytes = listener.queue.take();
            return defineClass(className, bytes, 0, bytes.length);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            websocket.removeWebSocketListener(listener);
        }
    }

    static class  ClassReceiveListener implements WebSocketByteListener {
        private BlockingQueue<byte[]> queue;
        private String className;

        public ClassReceiveListener(String className) {
            this.className = className;
            queue = new ArrayBlockingQueue<byte[]>(1);
        }

        @Override
        public void onMessage(byte[] bytes) {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            try {
                ClassResponse response = (ClassResponse) new FressianReader(bais).readObject();
                if (response.getClassName().equals(className))
                queue.put(response.getBytes());
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

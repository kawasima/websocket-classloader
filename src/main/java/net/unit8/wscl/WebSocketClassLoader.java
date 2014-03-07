package net.unit8.wscl;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketByteListener;
import com.ning.http.client.websocket.WebSocketUpgradeHandler;
import net.unit8.wscl.handler.ClassRequestWriteHandler;
import net.unit8.wscl.handler.ClassResponseReadHandler;
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
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * ClassLoader fetching classes via WebSocket.
 *
 * @author kawasima
 */
public class WebSocketClassLoader extends ClassLoader {
    private WebSocket websocket;
    private static Logger logger = LoggerFactory.getLogger(WebSocketClassLoader.class);

    public WebSocketClassLoader(String url) {
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
            logger.debug("new websocket classloader");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public Class<?> loadClass(String className, boolean resolve)
            throws ClassNotFoundException {
        Class<?> clazz = findLoadedClass(className);
        if (clazz != null) {
            logger.debug("Load class:" + className);
            return clazz;
        }
        try {
            return getParent().loadClass(className);
        } catch (ClassNotFoundException ex) {
            return defineClass(className, resolve);
        }
    }

    private Class<?> defineClass(String className, boolean resolve) {
        ClassReceiveListener listener;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FressianWriter fw = new FressianWriter(baos, new ILookup<Class, Map<String, WriteHandler>>() {
            @Override
            public Map<String, WriteHandler> valAt(Class key) {
                return FressianUtils.map(ClassRequest.class.getName(),
                        new ClassRequestWriteHandler());
            }
        });
        try {
            fw.writeObject(new ClassRequest(className));
            listener = new ClassReceiveListener(className);
            websocket.addWebSocketListener(listener);
        } catch(IOException ex) {
            throw new RuntimeException(ex);
        }

        try {
            logger.debug("fetch class:" + className);
            websocket.sendMessage(baos.toByteArray());
            byte[] bytes = listener.queue.take();
            if (bytes == null || bytes.length == 0)
                throw new ClassNotFoundException(className);
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
                logger.debug("fetched class:" + className);
                ClassResponse response = (ClassResponse) new FressianReader(bais, new ILookup<Object, ReadHandler>() {
                    @Override
                    public ReadHandler valAt(Object key) {
                        if (key.equals(ClassResponse.class.getName()))
                            return new ClassResponseReadHandler();
                        else
                            return null;
                    }
                }).readObject();
                if (response.getClassName().equals(className)) {
                    byte[] classBytes = response.getBytes();
                    if (classBytes == null)
                        classBytes = new byte[0];
                    queue.put(classBytes);
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

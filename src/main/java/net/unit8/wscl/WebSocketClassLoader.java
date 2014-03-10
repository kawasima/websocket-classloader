package net.unit8.wscl;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketByteListener;
import com.ning.http.client.websocket.WebSocketUpgradeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

/**
 * ClassLoader fetching classes via WebSocket.
 *
 * @author kawasima
 */
public class WebSocketClassLoader extends ClassLoader {
    private WebSocket websocket;
    private URL baseUrl;

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
        try {
            URL httpUrl = new URL(url.replaceFirst("ws://", "http://"));
            baseUrl = new URL("ws", httpUrl.getHost(), httpUrl.getPort(), "",
                    new WebSocketURLStreamHandler(websocket));
        } catch (MalformedURLException e) {
            throw new RuntimeException("ClassProvider URL is invalid.", e);
        }

    }

    protected URL findResource(String name) {
        URL url;
        try {
            url = new URL(baseUrl.getProtocol(), baseUrl.getHost(), baseUrl.getPort(),
                    name, new WebSocketURLStreamHandler(websocket));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("name");
        }

        try {
            WebSocketURLConnection connection = (WebSocketURLConnection)url.openConnection();
            return connection.existsUrl() ? url : null;
        } catch(Exception e) {
            return null;
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

    private Class<?> defineClass(String className, boolean resolve)
            throws ClassNotFoundException {
        String path = className.replace('.', '/').concat(".class");
        URL url = findResource(path);
        if (url == null)
            throw new ClassNotFoundException(className);

        try {
            URLConnection connection = url.openConnection();
            byte[] bytes = (byte[]) connection.getContent();
            if (bytes != null) {
                return defineClass(className, bytes, 0, bytes.length);
            } else {
                throw new ClassNotFoundException(className);
            }
        } catch (Exception ex) {
            throw new ClassNotFoundException(className, ex);
        }
    }
}

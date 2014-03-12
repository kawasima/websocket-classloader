package net.unit8.wscl;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketByteListener;
import com.ning.http.client.websocket.WebSocketUpgradeHandler;
import net.unit8.wscl.util.DigestUtils;
import net.unit8.wscl.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;

/**
 * ClassLoader fetching classes via WebSocket.
 *
 * @author kawasima
 */
public class WebSocketClassLoader extends ClassLoader {
    private WebSocket websocket;
    private URL baseUrl;
    private File cacheDirectory;

    private static Logger logger = LoggerFactory.getLogger(WebSocketClassLoader.class);

    public WebSocketClassLoader(String url) {
        String cachePath = System.getProperty("wscl.cache.directory");
        if (cachePath != null) {
            cacheDirectory = new File(cachePath);
            if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
                throw new IllegalArgumentException(
                        "Can't create cache directory: " + cachePath);
            }
        }

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
                    new WebSocketURLStreamHandler(websocket, cacheDirectory));
        } catch (MalformedURLException e) {
            throw new RuntimeException("ClassProvider URL is invalid.", e);
        }
    }

    private URL findCache(URL url, byte[] digest) {
        File cacheFile = new File(cacheDirectory, url.getPath());
        if (cacheFile.exists() && Arrays.equals(digest, DigestUtils.md5hash(cacheFile))) {
            try {
                return cacheFile.toURI().toURL();
            } catch (MalformedURLException e) {
                return url;
            }
        } else {
            return url;
        }
    }

    protected URL findResource(String name) {
        URL url;
        try {
            url = new URL(baseUrl.getProtocol(), baseUrl.getHost(), baseUrl.getPort(),
                    name, new WebSocketURLStreamHandler(websocket, cacheDirectory));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("name");
        }

        try {
            WebSocketURLConnection connection = (WebSocketURLConnection)url.openConnection();
            byte[] digest = connection.getResourceDigest();
            if (digest == null)
                return null;
            return cacheDirectory != null ? findCache(url, digest) : url;
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
            byte[] bytes = IOUtils.slurp(connection.getContent());
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

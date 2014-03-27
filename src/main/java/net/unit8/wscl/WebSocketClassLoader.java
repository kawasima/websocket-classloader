package net.unit8.wscl;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketByteListener;
import com.ning.http.client.websocket.WebSocketUpgradeHandler;
import net.unit8.wscl.util.DigestUtils;
import net.unit8.wscl.util.IOUtils;
import net.unit8.wscl.util.PropertyUtils;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
        this(url, Thread.currentThread().getContextClassLoader());
    }
    public WebSocketClassLoader(String url, ClassLoader parent) {
        super(parent);
        cacheDirectory = PropertyUtils.getFileSystemProperty("wscl.cache.directory");

        if (cacheDirectory != null && !cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            throw new IllegalArgumentException(
                    "Can't create cache directory: " + cacheDirectory);
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
                    ).build())
                    .get(PropertyUtils.getLongSystemProperty("wscl.timeout", 5000), TimeUnit.MILLISECONDS);
            logger.debug("new websocket classloader");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        try {
            URL httpUrl = new URL(url.replaceFirst("ws://", "http://"));
            baseUrl = new URL("ws", httpUrl.getHost(), httpUrl.getPort(),
                    httpUrl.getFile(),
                    new WebSocketURLStreamHandler(websocket, cacheDirectory));
        } catch (Exception e) {
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
            QueryStringDecoder decoder = new QueryStringDecoder(baseUrl.toURI());
            List<String> classLoaderIds = decoder.getParameters().get("classLoaderId");

            StringBuilder file = new StringBuilder(256);
            file.append(name);

            if (classLoaderIds !=  null && !classLoaderIds.isEmpty())
                file.append("?classLoaderId=").append(classLoaderIds.get(0));

            url = new URL(baseUrl.getProtocol(), baseUrl.getHost(), baseUrl.getPort(),
                    file.toString(),
                    new WebSocketURLStreamHandler(websocket, cacheDirectory));
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("name");
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("name");
        }

        try {
            WebSocketURLConnection connection = (WebSocketURLConnection)url.openConnection();
            byte[] digest = connection.getResourceDigest();
            if (digest == null)
                return null;
            return cacheDirectory != null ? findCache(url, digest) : url;
        } catch(Exception e) {
            logger.warn("Exception at fetching.", e);
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

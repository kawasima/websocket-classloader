package net.unit8.wscl;

import io.netty.handler.codec.http.QueryStringDecoder;
import net.unit8.wscl.client.WebSocketClient;
import net.unit8.wscl.client.WebSocketListener;
import net.unit8.wscl.util.DigestUtils;
import net.unit8.wscl.util.IOUtils;
import net.unit8.wscl.util.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;

/**
 * ClassLoader fetching classes via WebSocket.
 *
 * @author kawasima
 */
public class WebSocketClassLoader extends ClassLoader {
    private WebSocketClient client;
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

        client = new WebSocketClient(url);
        try {
            client.addListener(
                    new WebSocketListener() {
                        @Override
                        public void onOpen(WebSocketClient c) {
                            logger.debug("Connected! to class provider.");
                        }
                    });
            client.connect(PropertyUtils.getLongSystemProperty("wscl.timeout", 5000));

            logger.debug("new websocket classloader");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        try {
            URL httpUrl = new URL(url.replaceFirst("ws://", "http://"));
            baseUrl = new URL("ws", httpUrl.getHost(), httpUrl.getPort(),
                    httpUrl.getFile(),
                    new WebSocketURLStreamHandler(client, cacheDirectory));
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
            List<String> classLoaderIds = decoder.parameters().get("classLoaderId");

            StringBuilder file = new StringBuilder(256);
            file.append(name);

            if (classLoaderIds !=  null && !classLoaderIds.isEmpty())
                file.append("?classLoaderId=").append(classLoaderIds.get(0));

            url = new URL(baseUrl.getProtocol(), baseUrl.getHost(), baseUrl.getPort(),
                    file.toString(),
                    new WebSocketURLStreamHandler(client, cacheDirectory));
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


    @Override
    protected Class<?> loadClass(String className, boolean resolve)
            throws ClassNotFoundException {
        logger.debug("obtain lock:" + className);
        synchronized (getClassLoadingLock(className)) {
            Class<?> clazz = findLoadedClass(className);
            if (clazz == null) {
                try {
                    clazz = getParent().loadClass(className);
                } catch (ClassNotFoundException ex) {
                }
                if (clazz == null)
                    clazz = findClass(className);
            }
            if (resolve) {
                resolveClass(clazz);
            }
            logger.debug("release lock:" + className);
            return clazz;
        }
    }

    @Override
    protected Class<?> findClass(String className) throws ClassNotFoundException {
        return defineClass(className, false);
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
                int idx = className.lastIndexOf(".");
                if (idx > 0) {
                    String packageName = className.substring(0, idx);
                    Package pkg = getPackage(packageName);
                    if (pkg == null) {
                        definePackage(packageName, null, null, null, null, null, null, null);
                    }
                }
                return defineClass(className, bytes, 0, bytes.length);
            } else {
                throw new ClassNotFoundException(className);
            }
        } catch (Exception ex) {
            throw new ClassNotFoundException(className, ex);
        }
    }

    @Override
    public void finalize() {
        dispose();
    }

    public void dispose() {
        if (client !=null && client.isOpen()) {
            client.close();
            client = null;
        }
    }
}

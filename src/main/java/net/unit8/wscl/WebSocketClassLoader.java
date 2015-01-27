package net.unit8.wscl;

import net.unit8.wscl.util.DigestUtils;
import net.unit8.wscl.util.IOUtils;
import net.unit8.wscl.util.PropertyUtils;
import net.unit8.wscl.util.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
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
    private Session session;
    private URL baseUrl;
    private File cacheDirectory;

    private static final Logger logger = LoggerFactory.getLogger(WebSocketClassLoader.class);

    public WebSocketClassLoader(String url) throws IOException, DeploymentException {
        this(url, Thread.currentThread().getContextClassLoader());
    }
    public WebSocketClassLoader(String url, ClassLoader parent)
            throws DeploymentException, IOException {
        super(parent);
        cacheDirectory = PropertyUtils.getFileSystemProperty("wscl.cache.directory");

        if (cacheDirectory != null && !cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            throw new IllegalArgumentException(
                    "Can't create cache directory: " + cacheDirectory);
        }
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        session = container.connectToServer(ClassLoaderEndpoint.class, URI.create(url));
        try {
            URL httpUrl = new URL(url.replaceFirst("ws://", "http://"));
            baseUrl = new URL("ws", httpUrl.getHost(), httpUrl.getPort(),
                    httpUrl.getFile(),
                    new WebSocketURLStreamHandler(session, cacheDirectory));
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
            QueryStringDecoder decoder = new QueryStringDecoder(baseUrl.getQuery());
            List<String> classLoaderIds = decoder.parameters().get("classLoaderId");

            StringBuilder file = new StringBuilder(256);
            file.append(name);

            if (classLoaderIds !=  null && !classLoaderIds.isEmpty())
                file.append("?classLoaderId=").append(classLoaderIds.get(0));

            url = new URL(baseUrl.getProtocol(), baseUrl.getHost(), baseUrl.getPort(),
                    file.toString(),
                    new WebSocketURLStreamHandler(session, cacheDirectory));
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
        synchronized (getClassLoadingLock(className)) {
            Class<?> clazz = findLoadedClass(className);
            if (clazz == null) {
                try {
                    clazz = getParent().loadClass(className);
                } catch (ClassNotFoundException ignored) {
                }
                if (clazz == null)
                    clazz = findClass(className);
            }
            if (resolve) {
                resolveClass(clazz);
            }
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
    public void finalize() throws Throwable{
        try {
            dispose();
        } finally {
            super.finalize();
        }
    }

    public void dispose() throws IOException {
        if (session != null && session.isOpen()) {
            session.close();
            session = null;
        }
    }
}

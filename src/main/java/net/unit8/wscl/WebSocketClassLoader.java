package net.unit8.wscl;

import net.unit8.wscl.util.DigestUtils;
import net.unit8.wscl.util.IOUtils;
import net.unit8.wscl.util.PropertyUtils;
import net.unit8.wscl.util.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.WebSocketContainer;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

/**
 * ClassLoader fetching classes via WebSocket.
 *
 * @author kawasima
 */
public class WebSocketClassLoader extends ClassLoader {
    private ClassLoaderEndpoint endpoint;
    private URL baseUrl;
    private File cacheDirectory;

    private static final Logger logger = LoggerFactory.getLogger(WebSocketClassLoader.class);

    public WebSocketClassLoader(String url) throws IOException, DeploymentException {
        this(url, Thread.currentThread().getContextClassLoader());
    }
    public WebSocketClassLoader(String url, ClassLoader parent)
            throws DeploymentException, IOException {
        super(parent);

        logger.debug("Parent classloader=" + parent);
        cacheDirectory = PropertyUtils.getFileSystemProperty("wscl.cache.directory");

        if (cacheDirectory != null && !cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            throw new IllegalArgumentException(
                    "Can't create cache directory: " + cacheDirectory);
        }
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        endpoint = new ClassLoaderEndpoint();
        container.connectToServer(endpoint,
                ClientEndpointConfig.Builder.create().build(), URI.create(url));
        try {
            URL httpUrl = new URL(url.replaceFirst("ws://", "http://"));
            QueryStringDecoder decoder = new QueryStringDecoder(httpUrl.getQuery());
            List<String> classLoaderIds = decoder.parameters().get("classLoaderId");
            WebSocketURLStreamHandler urlStreamHandler = new WebSocketURLStreamHandler(endpoint, cacheDirectory);
            if (classLoaderIds !=  null && !classLoaderIds.isEmpty())
                urlStreamHandler.setClassLoaderId(classLoaderIds.get(0));

            baseUrl = new URL("ws", httpUrl.getHost(), httpUrl.getPort(),
                    httpUrl.getFile(), urlStreamHandler);
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

    @Override
    protected URL findResource(String name) {
        URL url;
        try {
           StringBuilder file = new StringBuilder(256);
            if (!name.startsWith("/")) {
                file.append("/");
            }
            file.append(name);

            url = new URL(baseUrl, file.toString());

        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("name");
        }

        try {
            WebSocketURLConnection connection = (WebSocketURLConnection)url.openConnection();
            byte[] digest = connection.getResourceDigest();
            logger.debug("findResource:" + name + ":" + url.toString());
            if (digest == null)
                return null;
            return cacheDirectory != null ? findCache(url, digest) : url;
        } catch(Exception e) {
            logger.warn("Exception at fetching.", e);
            return null;
        }
    }

    /**
     * Returns an enumeration of URL objects representing all the resources with th given name.
     *
     * Currently, WebSocketClassLoader returns only the first element.
     *
     * @param name The name of a resource.
     * @return All founded resources.
     */
    @Override
    protected Enumeration<URL> findResources(String name) {
        URL url = findResource(name);
        Vector<URL> urls = new Vector<>();
        if (url != null) {
            urls.add(url);
        }
        return urls.elements();
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
        return defineClass(className);
    }
    private Class<?> defineClass(String className)
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
        endpoint.close();
    }
}

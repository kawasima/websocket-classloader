package net.unit8.wscl;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * @author kawasima
 */
public class WebSocketURLStreamHandler extends URLStreamHandler{
    private final ClassLoaderEndpoint endpoint;
    private final File cacheDirectory;

    public WebSocketURLStreamHandler(ClassLoaderEndpoint endpoint, File cacheDirectory) {
        this.endpoint = endpoint;
        this.cacheDirectory = cacheDirectory;
    }

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        return new WebSocketURLConnection(url, endpoint, cacheDirectory);
    }
}

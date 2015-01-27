package net.unit8.wscl;

import javax.websocket.Session;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * @author kawasima
 */
public class WebSocketURLStreamHandler extends URLStreamHandler{
    private final Session session;
    private final File cacheDirectory;

    public WebSocketURLStreamHandler(Session session, File cacheDirectory) {
        this.session = session;
        this.cacheDirectory = cacheDirectory;
    }

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        return new WebSocketURLConnection(url, session, cacheDirectory);
    }
}

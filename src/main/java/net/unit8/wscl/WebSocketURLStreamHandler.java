package net.unit8.wscl;

import com.ning.http.client.ws.WebSocket;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * @author kawasima
 */
public class WebSocketURLStreamHandler extends URLStreamHandler{
    private WebSocket ws;
    private File cacheDirectory;

    public WebSocketURLStreamHandler(WebSocket ws, File cacheDirectory) {
        this.ws = ws;
        this.cacheDirectory = cacheDirectory;
    }

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        return new WebSocketURLConnection(url, ws, cacheDirectory);
    }
}

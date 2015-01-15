package net.unit8.wscl;

import net.unit8.wscl.client.WebSocketClient;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * @author kawasima
 */
public class WebSocketURLStreamHandler extends URLStreamHandler{
    private WebSocketClient client;
    private File cacheDirectory;

    public WebSocketURLStreamHandler(WebSocketClient client, File cacheDirectory) {
        this.client = client;
        this.cacheDirectory = cacheDirectory;
    }

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        return new WebSocketURLConnection(url, client, cacheDirectory);
    }
}

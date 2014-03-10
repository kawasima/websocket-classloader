package net.unit8.wscl;

import com.ning.http.client.websocket.WebSocket;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * @author kawasima
 */
public class WebSocketURLStreamHandler extends URLStreamHandler{
    private WebSocket ws;
    public WebSocketURLStreamHandler(WebSocket ws) {
        this.ws = ws;
    }

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        return new WebSocketURLConnection(url, ws);
    }
}

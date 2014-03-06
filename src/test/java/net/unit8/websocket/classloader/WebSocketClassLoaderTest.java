package net.unit8.websocket.classloader;

/**
 * Created by tie199026 on 14/03/06.
 */
public class WebSocketClassLoaderTest {
    public static void main(String[] args) {
        ClassLoader loader = new WebSocketClassLoader(Thread.currentThread().getContextClassLoader(),
                "http://localhost:5000");

        Thread.currentThread().setContextClassLoader(loader);
    }
}

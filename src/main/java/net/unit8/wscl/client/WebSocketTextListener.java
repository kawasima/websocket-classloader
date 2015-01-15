package net.unit8.wscl.client;

/**
 * @author kawasima
 */
public abstract class WebSocketTextListener extends WebSocketListener {
    public abstract void onMessage(String message);
}

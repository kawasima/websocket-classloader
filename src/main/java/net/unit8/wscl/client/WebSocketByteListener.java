package net.unit8.wscl.client;

/**
 * @author kawasima
 */
public abstract class WebSocketByteListener extends WebSocketListener {
    public abstract void onMessage(byte[] bytes);

}

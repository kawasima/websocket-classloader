package net.unit8.wscl;

import java.lang.reflect.Method;

/**
 * @author kawasima
 */
public class ThinClientLauncher {
    public static void main(String[] args) {
        String loaderAddress = System.getProperty("wscl.loader");
        if (loaderAddress == null)
            loaderAddress = "ws://localhost:8080";
        WebSocketClassLoader cl = new WebSocketClassLoader(loaderAddress);
        try {
            Class<?> mainClass = cl.loadClass(args[0], true);
            Method mainMethod = mainClass.getMethod("main", String[].class);
            String[] remoteArgs = new String[args.length - 1];
            System.arraycopy(args, 1, remoteArgs, 0, remoteArgs.length);
            Thread.currentThread().setContextClassLoader(cl);
            mainMethod.invoke(null, new Object[] {remoteArgs} );
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}

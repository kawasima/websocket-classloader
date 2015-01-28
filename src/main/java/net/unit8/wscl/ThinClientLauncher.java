package net.unit8.wscl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * The launcher for a thin client.
 *
 * @author kawasima
 */
public class ThinClientLauncher {
    private static final Logger logger = LoggerFactory.getLogger(ThinClientLauncher.class);

    public static void main(String[] args) throws IOException {
        String loaderAddress = System.getProperty("wscl.loader");
        if (loaderAddress == null)
            loaderAddress = "ws://localhost:5000";
        WebSocketClassLoader cl = null;
        try {
            cl = new WebSocketClassLoader(loaderAddress);
            Class<?> mainClass = cl.loadClass(args[0], true);
            Method mainMethod = mainClass.getMethod("main", String[].class);
            String[] remoteArgs = new String[args.length - 1];
            System.arraycopy(args, 1, remoteArgs, 0, remoteArgs.length);
            Thread.currentThread().setContextClassLoader(cl);
            mainMethod.invoke(null, new Object[] {remoteArgs} );
            cl.dispose();
        } catch (Exception ex) {
            logger.error("Error in executing " + args[0], ex);
        } finally {
            if (cl != null)
                cl.dispose();
        }
    }
}

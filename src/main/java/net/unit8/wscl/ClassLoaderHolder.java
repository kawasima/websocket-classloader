package net.unit8.wscl;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.UUID;

/**
 * @author kawasima
 */
public class ClassLoaderHolder {
    private static final ClassLoaderHolder INSTANCE = new ClassLoaderHolder();
    private final HashMap<UUID, ClassLoader> clMap = new HashMap<>();

    private ClassLoaderHolder() {
    }

    public static ClassLoaderHolder getInstance() {
        return INSTANCE;
    }

    public UUID registerClasspath(URL[] urls, ClassLoader parent) {
        UUID classLoaderId = UUID.randomUUID();
        synchronized (clMap) {
            clMap.put(classLoaderId, new URLClassLoader(urls, parent));
        }
        return classLoaderId;
    }

    public ClassLoader get(UUID classLoaderId) {
        synchronized (clMap) {
            return clMap.get(classLoaderId);
        }
    }
}

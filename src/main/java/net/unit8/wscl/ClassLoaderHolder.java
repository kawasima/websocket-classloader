package net.unit8.wscl;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author kawasima
 */
public class ClassLoaderHolder {
    private static final ClassLoaderHolder INSTANCE = new ClassLoaderHolder();
    private final ConcurrentMap<UUID, ClassLoader> clMap = new ConcurrentHashMap<>();

    private ClassLoaderHolder() {
    }

    public static ClassLoaderHolder getInstance() {
        return INSTANCE;
    }

    public UUID registerClasspath(URL[] urls, ClassLoader parent) {
        UUID classLoaderId = UUID.randomUUID();
        clMap.put(classLoaderId, new URLClassLoader(urls, parent));
        return classLoaderId;
    }

    public ClassLoader get(UUID classLoaderId) {
        return clMap.get(classLoaderId);
    }
}

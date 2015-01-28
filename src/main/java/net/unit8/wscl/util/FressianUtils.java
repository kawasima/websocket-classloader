package net.unit8.wscl.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilities for fressian.
 *
 * @author kawasima
 */
public class FressianUtils {
    @SuppressWarnings("unchecked")
    public static <K,V> Map<K,V> map(Object... keyvals) {
        if (keyvals == null) {
            return new HashMap<>();
        } else if (keyvals.length % 2 != 0) {
            throw new IllegalArgumentException("Map must have an even number of elements");
        } else {
            Map<K, V> m = new HashMap<>(keyvals.length / 2);
            for (int i = 0; i < keyvals.length; i += 2) {
                m.put((K)keyvals[i], (V)keyvals[i + 1]);
            }
            return Collections.unmodifiableMap(m);
        }
    }
    
    private FressianUtils()    //class FressianUtils cannot be instantiated.
    {
    	
    }
}

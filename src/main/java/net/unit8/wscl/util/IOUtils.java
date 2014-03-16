package net.unit8.wscl.util;

import java.io.*;
import java.net.URL;

/**
 * Utility for handling I/O.
 *
 * @author kawasima
 */
public class IOUtils {
    public static byte[] slurp(InputStream in) throws IOException {
        int n;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(16384);
        byte[] data = new byte[4096];
        while((n = in.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();

    }

    public static byte[] slurp(URL url) throws IOException {
        InputStream in = null;
        try {
            in = url.openStream();
            return slurp(in);
        } finally {
            closeQuietly(in);
        }
    }

    public static byte[] slurp(Object obj) throws IOException {
        if (obj instanceof InputStream) {
            return slurp((InputStream) obj);
        } else if (obj instanceof byte[]) {
            return (byte[]) obj;
        } else {
            throw new IllegalArgumentException("Unknown object: " + obj);
        }
    }

    public static byte[] slurpQuietly(URL url) {
        try {
            return slurp(url);
        } catch (IOException ex) {
            return null;
        }
    }

    public static void spit(File file, byte[] content) throws IOException{
        if(file == null)
            throw new FileNotFoundException("null");

        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            throw new IOException("Can't create directory" + file.getParent());
        }

        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(content);
        } finally {
            IOUtils.closeQuietly(out);
        }
    }

    public static void spitQuietly(File file, byte[] content) {
        try {
            IOUtils.spit(file, content);
        } catch (IOException ignore) {

        }
    }

    public static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null)
                closeable.close();
        } catch (IOException ignore) {

        }
    }
    
    private IOUtils()     //class IOUtils cannot be instantiated.
    {
    	
    }
}

package net.unit8.wscl.util;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for MessageDigest.
 *
 * @author kawasima
 */
public class DigestUtils {
    public static byte[] md5hash(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Unknown algorithm: MD5");
        }
    }

    public static byte[] md5hash(File input) {
        InputStream in = null;
        byte[] buf = new byte[4096];
        int n;
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            in = new BufferedInputStream(new FileInputStream(input));
            while((n = in.read(buf, 0, buf.length)) != -1) {
                digest.update(buf, 0, n);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Unknown algorithm: MD5");
        } catch (IOException e) {
            throw new RuntimeException(String.format("Can't read %s.", input), e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignore) {}
            }
        }
    }

}

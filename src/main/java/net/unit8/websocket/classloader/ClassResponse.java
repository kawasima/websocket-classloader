package net.unit8.websocket.classloader;

/**
 * Created by tie199026 on 14/03/06.
 */
public class ClassResponse {
    private String className;
    private byte[] bytes;

    public ClassResponse(String className, byte[] bytes) {
        this.className = className;
        this.bytes = bytes;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }
}

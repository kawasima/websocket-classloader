package net.unit8.wscl;

/**
 * Response for loading a class.
 *
 * @author kawasima
 */
public class ClassResponse {
    private String className;
    private byte[] bytes;

    public ClassResponse(String className) {
        this.className = className;
    }

    public ClassResponse(String className, byte[] bytes) {
        this(className);
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

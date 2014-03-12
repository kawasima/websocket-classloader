package net.unit8.wscl.dto;

/**
 * Response for loading a resource.
 *
 * @author kawasima
 */
public class ResourceResponse {
    private String resourceName;
    private byte[] bytes;
    private byte[] digest;

    public ResourceResponse(String resourceName) {
        this.resourceName = resourceName;
        this.digest = null;
    }

    public ResourceResponse(String resourceName, byte[] bytes, byte[] digest) {
        this(resourceName);
        this.bytes = bytes;
        this.digest = digest;
    }


    public byte[] getDigest() {
        return digest;
    }

    public void setDigest(byte[] digest) {
        this.digest = digest;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }
}

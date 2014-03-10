package net.unit8.wscl.dto;

/**
 * Response for loading a resource.
 *
 * @author kawasima
 */
public class ResourceResponse {
    private String resourceName;
    private byte[] bytes;
    private Boolean exists;

    public ResourceResponse(String resourceName) {
        this.resourceName = resourceName;
        this.exists = true;
    }

    public ResourceResponse(String resourceName, byte[] bytes, Boolean exists) {
        this(resourceName);
        this.bytes = bytes;
        this.exists = exists;
    }


    public Boolean exists() {
        return exists;
    }

    public void notFound() {
        this.exists = false;
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

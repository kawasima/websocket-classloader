package net.unit8.wscl.dto;

/**
 * Request for loading a resource.
 *
 * @author kawasima
 */
public class ResourceRequest {
    private String resourceName;
    private Boolean checkOnly = false;

    public ResourceRequest(String resourceName) {
        this.resourceName = resourceName;
    }

    public ResourceRequest(String resourceName, Boolean checkOnly) {
        this(resourceName);
        this.checkOnly = checkOnly;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public boolean isCheckOnly() {
        return checkOnly;
    }
}

package net.unit8.wscl;

import net.unit8.wscl.dto.ResourceRequest;
import net.unit8.wscl.dto.ResourceResponse;
import net.unit8.wscl.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.UUID;

/**
 * @author kawasima
 */
public class WebSocketURLConnection extends URLConnection {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketURLConnection.class);
    private final ClassLoaderEndpoint endpoint;
    private final File cacheDirectory;
    private UUID classLoaderId;

    public WebSocketURLConnection(URL url, ClassLoaderEndpoint endpoint, File cacheDirectory, UUID classLoaderId) {
        super(url);
        this.endpoint = endpoint;
        this.cacheDirectory = cacheDirectory;
        this.classLoaderId = classLoaderId;
    }

    @Override
    public void connect() {
        // Do nothing.
    }

    private String getResourcePath() {
        String path = getURL().getPath();
        if (path.startsWith("/")) {
            return path.substring(1);
        } else {
            return path;
        }
    }

    private ResourceResponse doRequest(ResourceRequest request) throws IOException {
        if (classLoaderId != null) {
            request.setClassLoaderId(classLoaderId);
        }
        ResourceResponse response =  endpoint.request(request);
        if (cacheDirectory != null && !request.isCheckOnly()) {
            IOUtils.spitQuietly(
                    new File(cacheDirectory, url.getPath()),
                    response.getBytes());
        }
        return response;
    }

    protected byte[] getResourceDigest() throws IOException {
        String resourcePath = getResourcePath();
        ResourceResponse response = doRequest(new ResourceRequest(resourcePath, true));
        return response.getDigest();
    }

    @Override
    public InputStream getInputStream() {
        if (!"ws".equalsIgnoreCase(getURL().getProtocol())) {
            try {
                return getURL().openStream();
            } catch (IOException ex) {
                return null;
            }
        }
        String resourcePath = getResourcePath();
        try {
            ResourceResponse response = doRequest(new ResourceRequest(resourcePath));
            if (response.getBytes() != null) {
                return new ByteArrayInputStream(response.getBytes());
            } else {
                return null;
            }
        } catch (IOException ex) {
            logger.debug("Can't retrieve resources.", ex);
            return null;
        }
    }

    @Override
    public Object getContent() {
        if (!"ws".equalsIgnoreCase(getURL().getProtocol())) {
            return IOUtils.slurpQuietly(getURL());
        }
        String resourcePath = getResourcePath();
        try {
            ResourceResponse response = doRequest(new ResourceRequest(resourcePath));
            return response.getBytes();
        } catch (IOException ex) {
            logger.debug("Can't retrieve resources.", ex);
            return null;
        }
    }
}

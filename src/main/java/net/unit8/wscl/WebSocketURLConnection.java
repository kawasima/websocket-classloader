package net.unit8.wscl;

import net.unit8.wscl.dto.ResourceRequest;
import net.unit8.wscl.dto.ResourceResponse;
import net.unit8.wscl.util.IOUtils;
import net.unit8.wscl.util.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.UUID;

/**
 * @author kawasima
 */
public class WebSocketURLConnection extends URLConnection {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketURLConnection.class);
    private final ClassLoaderEndpoint endpoint;
    private final File cacheDirectory;
    private UUID classLoaderId;

    public WebSocketURLConnection(URL url, ClassLoaderEndpoint endpoint, File cacheDirectory) {
        super(url);
        this.endpoint = endpoint;
        this.cacheDirectory = cacheDirectory;
        String query = url.getQuery();
        if (query != null) {
            List<String> classLoaderIds = new QueryStringDecoder(query).parameters().get("classLoaderId");
            if (!classLoaderIds.isEmpty()) {
                this.classLoaderId = UUID.fromString(classLoaderIds.get(0));
            }
        }
    }

    @Override
    public void connect() {
        // Do nothing.
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
        String resourcePath = getURL().getPath();
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
        String resourcePath = getURL().getPath();
        try {
            ResourceResponse response = doRequest(new ResourceRequest(resourcePath));
            return new ByteArrayInputStream(response.getBytes());
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
        String resourcePath = getURL().getPath();
        try {
            ResourceResponse response = doRequest(new ResourceRequest(resourcePath));
            return response.getBytes();
        } catch (IOException ex) {
            logger.debug("Can't retrieve resources.", ex);
            return null;
        }
    }
}

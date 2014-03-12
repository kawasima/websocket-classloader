package net.unit8.wscl.handler;

import net.unit8.wscl.dto.ResourceResponse;
import org.fressian.Writer;
import org.fressian.handlers.WriteHandler;

import java.io.IOException;

/**
 * WriteHandler for ResourceResponse
 *
 * @author kawasima
 */
public class ResourceResponseWriteHandler implements WriteHandler {
    @Override
    public void write(Writer w, Object instance) throws IOException {
        w.writeTag(ResourceResponse.class.getName(), 3);
        ResourceResponse response = (ResourceResponse) instance;
        w.writeString(response.getResourceName());
        w.writeBytes(response.getBytes());
        w.writeBytes(response.getDigest());
    }
}

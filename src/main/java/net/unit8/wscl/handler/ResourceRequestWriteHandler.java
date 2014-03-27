package net.unit8.wscl.handler;

import net.unit8.wscl.dto.ResourceRequest;
import org.fressian.Writer;
import org.fressian.handlers.WriteHandler;

import java.io.IOException;

/**
 * WriteHandler for ResourceRequest
 *
 * @author kawasima
 */
public class ResourceRequestWriteHandler implements WriteHandler{
    @Override
    public void write(Writer w, Object instance) throws IOException {
        w.writeTag(ResourceRequest.class.getName(), 3);
        ResourceRequest request = (ResourceRequest) instance;
        w.writeObject(request.getResourceName());
        w.writeBoolean(request.isCheckOnly());
        w.writeObject(request.getClassLoaderId());
    }
}

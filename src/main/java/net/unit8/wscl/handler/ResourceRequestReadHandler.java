package net.unit8.wscl.handler;

import net.unit8.wscl.dto.ResourceRequest;
import org.fressian.Reader;
import org.fressian.handlers.ReadHandler;

import java.io.IOException;

/**
 * ReadHandler for ResourceRequest
 *
 * @author kawasima
 */
public class ResourceRequestReadHandler implements ReadHandler {
    @Override
    public Object read(Reader r, Object tag, int componentCount) throws IOException {
        assert(componentCount == 2);
        return new ResourceRequest((String)r.readObject(), r.readBoolean());
    }
}

package net.unit8.wscl.handler;

import net.unit8.wscl.dto.ResourceResponse;
import org.fressian.Reader;
import org.fressian.handlers.ReadHandler;

import java.io.IOException;

/**
 * ReadHandler for ResourceResponse
 *
 * @author kawasima
 */
public class ResourceResponseReadHandler implements ReadHandler {
    @Override
    public Object read(Reader r, Object tag, int componentCount) throws IOException {
        assert(componentCount == 3);
        return new ResourceResponse(
                (String)r.readObject(),
                (byte[])r.readObject(),
                r.readBoolean());
    }
}

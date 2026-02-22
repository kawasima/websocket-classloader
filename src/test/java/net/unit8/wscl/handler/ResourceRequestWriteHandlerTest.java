package net.unit8.wscl.handler;

import net.unit8.wscl.dto.ResourceRequest;
import net.unit8.wscl.util.FressianUtils;
import org.fressian.FressianReader;
import org.fressian.FressianWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for ResourceRequestWriteHandler.
 *
 * @author kawasima
 */
public class ResourceRequestWriteHandlerTest {
    @Test
    public void test() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ResourceRequest req = new ResourceRequest("hoge");
        UUID uuid = UUID.randomUUID();
        req.setClassLoaderId(uuid);
        FressianWriter fw = new FressianWriter(baos,
                key -> key.equals(ResourceRequest.class)
                        ? FressianUtils.map(ResourceRequest.class.getName(), new ResourceRequestWriteHandler())
                        : null);
        fw.writeObject(req);
        FressianReader fr = new FressianReader(new ByteArrayInputStream(baos.toByteArray()),
                key -> key.equals(ResourceRequest.class.getName()) ? new ResourceRequestReadHandler() : null);
        ResourceRequest restoredReq = (ResourceRequest) fr.readObject();
        assertEquals(uuid, restoredReq.getClassLoaderId());
    }
}

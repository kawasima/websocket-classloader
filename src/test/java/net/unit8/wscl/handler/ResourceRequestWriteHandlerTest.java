package net.unit8.wscl.handler;

import net.unit8.wscl.dto.ResourceRequest;
import net.unit8.wscl.util.FressianUtils;
import org.fressian.FressianReader;
import org.fressian.FressianWriter;
import org.fressian.handlers.ILookup;
import org.fressian.handlers.ReadHandler;
import org.fressian.handlers.WriteHandler;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

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
        FressianWriter fw = new FressianWriter(baos, new ILookup<Class, Map<String, WriteHandler>>() {
            @Override
            public Map<String, WriteHandler> valAt(Class key) {
                if (key.equals(ResourceRequest.class)) {
                    return FressianUtils.map(ResourceRequest.class.getName(),
                            new ResourceRequestWriteHandler());
                } else {
                    return null;
                }

            }
        });
        fw.writeObject(req);
        FressianReader fr = new FressianReader(new ByteArrayInputStream(baos.toByteArray()),
                new ILookup<Object, ReadHandler>() {
                    @Override
                    public ReadHandler valAt(Object key) {
                        if (key.equals(ResourceRequest.class.getName()))
                            return new ResourceRequestReadHandler();
                        else
                            return null;
                    }
                });
        ResourceRequest restoredReq = (ResourceRequest) fr.readObject();
        Assert.assertEquals(uuid, restoredReq.getClassLoaderId());
    }
}

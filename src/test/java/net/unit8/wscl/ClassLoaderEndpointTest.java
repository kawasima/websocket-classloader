package net.unit8.wscl;

import net.unit8.wscl.dto.ResourceRequest;
import net.unit8.wscl.dto.ResourceResponse;
import org.fressian.FressianWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;

/**
 * @author kawasima
 */
@ExtendWith(MockitoExtension.class)
public class ClassLoaderEndpointTest {
    private ClassLoaderEndpoint cle;
    private Session session;
    private ResourceRequest resourceRequest;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeEach
    public void setup() throws Exception {
        cle = new ClassLoaderEndpoint();
        session = mock(Session.class);
        RemoteEndpoint.Async remoteEndpoint = mock(RemoteEndpoint.Async.class);
        lenient().doReturn(remoteEndpoint).when(session).getAsyncRemote();
        lenient().doReturn(null).when(remoteEndpoint).sendBinary(any(ByteBuffer.class));
        resourceRequest = mock(ResourceRequest.class);
        lenient().doReturn(UUID.randomUUID()).when(resourceRequest).getClassLoaderId();
        lenient().doReturn("resource1").when(resourceRequest).getResourceName();
    }

    private Callable<String> createTask(ClassLoaderEndpoint cle, ResourceRequest req) {
        return () -> request(cle, req);
    }

    @Test
    public void oneRequest() throws Exception {
        try (MockedConstruction<FressianWriter> ignored = Mockito.mockConstruction(FressianWriter.class,
                (mock, context) -> lenient().doReturn(mock).when(mock).writeObject(any()))) {
            cle.onOpen(session, null);
            ExecutorService executor = Executors.newCachedThreadPool();
            Future<String> res = executor.submit(createTask(cle, resourceRequest));
            Field f = cle.getClass().getDeclaredField("waitingResponses");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, BlockingQueue<ResourceResponse>> waitingResponse =
                    (ConcurrentHashMap<String, BlockingQueue<ResourceResponse>>) f.get(cle);
            Thread.sleep(1000);
            BlockingQueue<ResourceResponse> queue = waitingResponse.get("resource1");
            queue.offer(new ResourceResponse("resource name1"));
            assertEquals("resource name1", res.get(1500, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void twoRequestAtTheSameTime() throws Exception {
        try (MockedConstruction<FressianWriter> ignored = Mockito.mockConstruction(FressianWriter.class,
                (mock, context) -> lenient().doReturn(mock).when(mock).writeObject(any()))) {
            cle.onOpen(session, null);
            ExecutorService executor = Executors.newCachedThreadPool();
            Future<String> res1 = executor.submit(createTask(cle, resourceRequest));
            Future<String> res2 = executor.submit(createTask(cle, resourceRequest));

            Field f = cle.getClass().getDeclaredField("waitingResponses");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, BlockingQueue<ResourceResponse>> waitingResponse =
                    (ConcurrentHashMap<String, BlockingQueue<ResourceResponse>>) f.get(cle);
            Thread.sleep(1000);
            BlockingQueue<ResourceResponse> queue = waitingResponse.get("resource1");
            queue.offer(new ResourceResponse("resource name1"));
            queue.offer(new ResourceResponse("resource name1"));
            assertEquals("resource name1", res1.get());
            assertEquals("resource name1", res2.get());
        }
    }

    String request(ClassLoaderEndpoint cle, ResourceRequest resourceRequest) {
        try {
            return cle.request(resourceRequest).getResourceName();
        } catch (IOException e) {
            fail();
        }
        return null;
    }
}

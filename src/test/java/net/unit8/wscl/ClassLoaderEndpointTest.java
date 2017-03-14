package net.unit8.wscl;

import net.unit8.wscl.dto.ResourceRequest;
import net.unit8.wscl.dto.ResourceResponse;
import org.fressian.FressianWriter;
import org.fressian.Writer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.Matchers.*;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.*;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * @author kawasima
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({FressianWriter.class, ClassLoaderEndpoint.class})
public class ClassLoaderEndpointTest {
    private ClassLoaderEndpoint cle;
    private Session session;
    private ResourceRequest resourceRequest;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Before
    public void setup() throws Exception{
        //setup mocks
        cle = new ClassLoaderEndpoint();
        session = mock(Session.class);
        RemoteEndpoint remoteEndpoint = mock(RemoteEndpoint.Async.class);
        doReturn(remoteEndpoint).when(session).getAsyncRemote();
        ((RemoteEndpoint.Async) doReturn(null).when(remoteEndpoint)).sendBinary(any(ByteBuffer.class));
        resourceRequest = mock(ResourceRequest.class);
        doReturn(UUID.randomUUID()).when(resourceRequest).getClassLoaderId();
        doReturn("resource1").when(resourceRequest).getResourceName();
        FressianWriter fressianWriter = mock(FressianWriter.class);
        doReturn(mock(Writer.class)).when(fressianWriter).writeObject(any(ResourceRequest.class));
        PowerMockito.whenNew(FressianWriter.class).withAnyArguments().thenReturn(fressianWriter);
    }


    private Callable<String> createTask(final ClassLoaderEndpoint cle, final ResourceRequest req) {
        return new Callable<String>() {
            public String call() throws Exception {
                return request(cle, req);
            }
        };
    }

    @Test
    public void oneRequest() throws Exception {
        cle.onOpen(session, null);
        ExecutorService executor = Executors.newCachedThreadPool();
        Future<String> res = executor.submit(createTask(cle, resourceRequest));
        Field f = cle.getClass().getDeclaredField("waitingResponses");
        f.setAccessible(true);
        @SuppressWarnings("unchecked") ConcurrentHashMap<String, BlockingQueue<ResourceResponse>> waitingResponse = (ConcurrentHashMap<String, BlockingQueue<ResourceResponse>>) f.get(cle);
        Thread.sleep(1000);
        BlockingQueue<ResourceResponse> queue = waitingResponse.get("resource1");
        queue.offer(new ResourceResponse("resource name1"));
        assertThat(res.get(1500, TimeUnit.MILLISECONDS),is("resource name1"));
    }

    @Test
    public void twoRequestAtTheSameTime() throws Exception {
        cle.onOpen(session, null);
        ExecutorService executor = Executors.newCachedThreadPool();
        Future<String> res1 = executor.submit(createTask(cle, resourceRequest));
        Future<String> res2 = executor.submit(createTask(cle, resourceRequest));

        Field f = cle.getClass().getDeclaredField("waitingResponses");
        f.setAccessible(true);
        @SuppressWarnings("unchecked") ConcurrentHashMap<String, BlockingQueue<ResourceResponse>> waitingResponse = (ConcurrentHashMap<String, BlockingQueue<ResourceResponse>>) f.get(cle);
        Thread.sleep(1000);
        BlockingQueue<ResourceResponse> queue = waitingResponse.get("resource1");
        queue.offer(new ResourceResponse("resource name1"));
        queue.offer(new ResourceResponse("resource name1"));
        assertThat(res1.get(),is("resource name1"));
        assertThat(res2.get(),is("resource name1"));
    }

    String request(ClassLoaderEndpoint cle, ResourceRequest resourceRequest){
        try {
            return cle.request(resourceRequest).getResourceName();
        } catch (IOException e) {
            fail();
        }
        return null;
    }
}

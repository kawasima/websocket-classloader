package net.unit8.wscl;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.websocket.RemoteEndpoint;
import javax.websocket.RemoteEndpoint.Async;
import javax.websocket.Session;

import org.fressian.FressianWriter;
import org.fressian.Writer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import net.unit8.wscl.dto.ResourceRequest;
import net.unit8.wscl.dto.ResourceResponse;

@RunWith(PowerMockRunner.class)
@PrepareForTest({FressianWriter.class,ClassLoaderEndpoint.class})
public class ClassLoaderEndpointTest {
    private ClassLoaderEndpoint cle;
    private Session session;
    private RemoteEndpoint remoteEndpoint;
    private ResourceRequest resourceRequest;
    private FressianWriter fressianWriter;
    
    @Before
    public void setup() throws Exception{
        //setup mocks
        cle = new ClassLoaderEndpoint();
        session = mock(Session.class);
        remoteEndpoint = mock(Async.class);
        doReturn(remoteEndpoint).when(session).getAsyncRemote();
        ((Async) doReturn(null).when(remoteEndpoint)).sendBinary(any(ByteBuffer.class));
        resourceRequest = mock(ResourceRequest.class);
        doReturn(UUID.randomUUID()).when(resourceRequest).getClassLoaderId();
        doReturn("resouce1").when(resourceRequest).getResourceName();
        fressianWriter = mock(FressianWriter.class);
        doReturn(mock(Writer.class)).when(fressianWriter).writeObject(any(ResourceRequest.class));
        PowerMockito.whenNew(FressianWriter.class).withAnyArguments().thenReturn(fressianWriter );
    }
    
    

    @Test
    public void oneRequest() throws Exception {
        cle.onOpen(session, null);
        ExecutorService service = Executors.newCachedThreadPool();
        CompletableFuture<String> asyncResponse = CompletableFuture.supplyAsync(() -> request(cle,resourceRequest), service);
        Field f = cle.getClass().getDeclaredField("waitingResponses");
        f.setAccessible(true);
        ConcurrentHashMap waitingResponse = (ConcurrentHashMap) f.get(cle);
        Thread.sleep(1000);
        BlockingQueue<ResourceResponse> queue = (BlockingQueue<ResourceResponse>) waitingResponse.get("resouce1");
        queue.offer(new ResourceResponse("resoucename1"));
        assertThat(asyncResponse.get(),is("resoucename1"));
    }
    
    @Test
    public void twoRequestAtTheSameTime() throws Exception {
        cle.onOpen(session, null);
        ExecutorService service = Executors.newCachedThreadPool();
        CompletableFuture<String> asyncResponse1 = CompletableFuture.supplyAsync(() -> request(cle,resourceRequest), service);
        CompletableFuture<String> asyncResponse2 = CompletableFuture.supplyAsync(() -> request(cle,resourceRequest), service);
        Field f = cle.getClass().getDeclaredField("waitingResponses");
        f.setAccessible(true);
        ConcurrentHashMap waitingResponse = (ConcurrentHashMap) f.get(cle);
        Thread.sleep(1000);
        BlockingQueue<ResourceResponse> queue = (BlockingQueue<ResourceResponse>) waitingResponse.get("resouce1");
        queue.offer(new ResourceResponse("resoucename1"));
        queue.offer(new ResourceResponse("resoucename1"));
        assertThat(asyncResponse1.get(),is("resoucename1"));
        assertThat(asyncResponse2.get(),is("resoucename1"));
    }

    @Test
    public void manyRequestAtTheSameTime() throws Exception {
        cle.onOpen(session, null);
        ExecutorService service = Executors.newCachedThreadPool();
        CompletableFuture<String> asyncResponse1 = CompletableFuture.supplyAsync(() -> request(cle,resourceRequest), service);
        CompletableFuture<String> asyncResponse2 = CompletableFuture.supplyAsync(() -> request(cle,resourceRequest), service);
        CompletableFuture<String> asyncResponse3 = CompletableFuture.supplyAsync(() -> request(cle,resourceRequest), service);
        Field f = cle.getClass().getDeclaredField("waitingResponses");
        f.setAccessible(true);
        ConcurrentHashMap waitingResponse = (ConcurrentHashMap) f.get(cle);
        Thread.sleep(1000);
        BlockingQueue<ResourceResponse> queue = (BlockingQueue<ResourceResponse>) waitingResponse.get("resouce1");
        queue.offer(new ResourceResponse("resoucename1"));
        queue.offer(new ResourceResponse("resoucename1"));
        queue.offer(new ResourceResponse("resoucename1"));

        assertThat(asyncResponse1.get(),is("resoucename1"));
        assertThat(asyncResponse2.get(),is("resoucename1"));
        assertThat(asyncResponse3.get(),is("resoucename1"));
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

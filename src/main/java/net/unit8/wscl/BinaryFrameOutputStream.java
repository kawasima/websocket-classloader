package net.unit8.wscl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * The output stream for BinaryFrame.
 *
 * @author kawasima
 */
public class BinaryFrameOutputStream extends OutputStream {
    private static final Logger logger = LoggerFactory.getLogger(BinaryFrameOutputStream.class);
    private static final int DEFAULT_BUFFER_SIZE = 65536;
    private final ByteBuffer buffer;
    private final RemoteEndpoint.Async remote;

    public BinaryFrameOutputStream(RemoteEndpoint.Async remote) {
        this.buffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
        this.remote = remote;
    }

    @Override
    public void write(int b) throws IOException {
        if (!buffer.hasRemaining()) flush();
        buffer.put((byte) b);
    }

    @Override
    public void write(@SuppressWarnings("NullableProblems") byte[] bytes, int offset, int length) throws IOException {
        if (buffer.remaining() < length) flush();
        buffer.put(bytes, offset, length);
    }

    @Override
    public void flush() {
        if (buffer.position() > 0) {
            buffer.flip();
            remote.sendBinary(buffer, new SendHandler() {
                @Override
                public void onResult(SendResult sendResult) {
                    if (!sendResult.isOK()) {
                        logger.error("Failed to send messages.", sendResult.getException());
                    }
                }
            });
        }
        buffer.clear();
    }

    @Override
    public void close() {
        flush();
    }
}

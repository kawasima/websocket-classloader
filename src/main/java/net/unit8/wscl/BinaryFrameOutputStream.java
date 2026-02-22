package net.unit8.wscl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.websocket.RemoteEndpoint;
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
    public synchronized void write(int b) throws IOException {
        if (!buffer.hasRemaining()) flush();
        buffer.put((byte) b);
    }

    @Override
    public synchronized void write(@SuppressWarnings("NullableProblems") byte[] bytes, int offset, int length) throws IOException {
        if (buffer.remaining() < length) flush();
        buffer.put(bytes, offset, length);
    }

    @Override
    public synchronized void flush() {
        if (buffer.position() > 0) {
            buffer.flip();
            ByteBuffer copy = ByteBuffer.allocate(buffer.remaining());
            copy.put(buffer);
            copy.flip();
            remote.sendBinary(copy, sendResult -> {
                if (!sendResult.isOK()) {
                    logger.error("Failed to send messages.", sendResult.getException());
                }
            });
        }
        buffer.clear();
    }

    @Override
    public synchronized void close() {
        flush();
    }
}

package net.unit8.wscl.handler;

import net.unit8.wscl.ClassResponse;
import org.fressian.Writer;
import org.fressian.handlers.WriteHandler;

import java.io.IOException;

/**
 * WriteHandler for ClassResponse
 *
 * @author kawasima
 */
public class ClassResponseWriteHandler implements WriteHandler {
    @Override
    public void write(Writer w, Object instance) throws IOException {
        w.writeTag(ClassResponse.class.getName(), 2);
        ClassResponse response = (ClassResponse) instance;
        w.writeString(response.getClassName());
        w.writeBytes(response.getBytes());
    }
}

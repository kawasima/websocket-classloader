package net.unit8.wscl.handler;

import net.unit8.wscl.ClassResponse;
import org.fressian.Reader;
import org.fressian.handlers.ReadHandler;

import java.io.IOException;

/**
 * ReadHandler for ClassResponse
 *
 * @author kawasima
 */
public class ClassResponseReadHandler implements ReadHandler {
    @Override
    public Object read(Reader r, Object tag, int componentCount) throws IOException {
        assert(componentCount == 2);
        return new ClassResponse((String)r.readObject(), (byte[])r.readObject());
    }
}

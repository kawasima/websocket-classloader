package net.unit8.wscl.handler;

import net.unit8.wscl.ClassRequest;
import org.fressian.Reader;
import org.fressian.handlers.ReadHandler;

import java.io.IOException;

/**
 * ReadHandler for ClassRequest
 *
 * @author kawasima
 */
public class ClassRequestReadHandler implements ReadHandler {
    @Override
    public Object read(Reader r, Object tag, int componentCount) throws IOException {
        assert(componentCount == 1);
        return new ClassRequest((String)r.readObject());
    }
}

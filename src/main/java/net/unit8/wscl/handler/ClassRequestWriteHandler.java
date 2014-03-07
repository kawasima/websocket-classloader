package net.unit8.wscl.handler;

import org.fressian.Writer;
import org.fressian.handlers.WriteHandler;

import net.unit8.wscl.ClassRequest;

import java.io.IOException;

/**
 * WriteHandler for ClassRequest
 *
 * @author kawasima
 */
public class ClassRequestWriteHandler implements WriteHandler{
    @Override
    public void write(Writer w, Object instance) throws IOException {
        w.writeTag(ClassRequest.class.getName(), 1);
        ClassRequest request = (ClassRequest) instance;
        w.writeObject(request.getClassName());
    }
}

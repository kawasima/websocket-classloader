package net.unit8.wscl;

/**
 * Request for loading a class.
 *
 * @author kawasima
 */
public class ClassRequest {
    private String className;

    public ClassRequest(String className) {
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }
}

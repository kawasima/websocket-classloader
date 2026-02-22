package net.unit8.wscl;

import io.undertow.Undertow;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that starts a real ClassProvider server via Undertow
 * and verifies WebSocketClassLoader can load classes/resources over WebSocket.
 *
 * The server-side ClassLoader is isolated via ClassLoaderHolder so that
 * the test class is only available through the WebSocket transport,
 * not from the client's parent ClassLoader.
 *
 * @author kawasima
 */
class WebSocketClassLoaderIntegrationTest {

    private static Undertow server;
    private static final int PORT = 17654;

    /** classLoaderId registered with ClassLoaderHolder for the isolated classpath */
    private static UUID classLoaderId;

    /** The FQCN of the dummy class that only exists in the isolated classpath */
    private static final String DUMMY_CLASS_NAME = "integration.test.HelloDummy";

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void startServer() throws Exception {
        // Generate a minimal .class file for DUMMY_CLASS_NAME in a temp directory.
        // This class is only visible to the ClassLoader registered with ClassLoaderHolder.
        generateDummyClass(tempDir, DUMMY_CLASS_NAME);

        // Register the temp directory as a classpath in ClassLoaderHolder
        classLoaderId = ClassLoaderHolder.getInstance().registerClasspath(
                new URL[]{ tempDir.toUri().toURL() },
                WebSocketClassLoaderIntegrationTest.class.getClassLoader());

        Xnio xnio = Xnio.getInstance("nio", Undertow.class.getClassLoader());
        XnioWorker xnioWorker = xnio.createWorker(OptionMap.builder().getMap());
        WebSocketDeploymentInfo webSockets = new WebSocketDeploymentInfo()
                .addEndpoint(ClassProvider.class)
                .setWorker(xnioWorker);
        DeploymentManager deploymentManager = Servlets.defaultContainer()
                .addDeployment(Servlets.deployment()
                        .setClassLoader(WebSocketClassLoaderIntegrationTest.class.getClassLoader())
                        .setContextPath("/")
                        .setDeploymentName("class-provider-test")
                        .addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME, webSockets));

        deploymentManager.deploy();
        server = Undertow.builder()
                .addHttpListener(PORT, "localhost")
                .setHandler(deploymentManager.start())
                .build();
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    private String wsUrl() {
        return "ws://localhost:" + PORT + "/?classLoaderId=" + classLoaderId;
    }

    @Test
    void findResourceReturnsUrlForExistingResource() throws Exception {
        try (WebSocketClassLoader cl = new WebSocketClassLoader(wsUrl())) {
            URL url = cl.findResource("integration/test/HelloDummy.class");
            assertNotNull(url, "findResource should return a URL for an existing class file");
        }
    }

    @Test
    void findResourceReturnsNullForMissingResource() throws Exception {
        try (WebSocketClassLoader cl = new WebSocketClassLoader(wsUrl())) {
            URL url = cl.findResource("com/nonexistent/DoesNotExist.class");
            assertNull(url, "findResource should return null for a non-existent resource");
        }
    }

    @Test
    void loadClassFromIsolatedClasspath() throws Exception {
        // The parent is PlatformClassLoader, which does NOT have the dummy class.
        // The server-side ClassLoaderHolder has the dummy class registered
        // under classLoaderId. This proves the class is truly fetched over WebSocket.
        ClassLoader platformOnly = ClassLoader.getPlatformClassLoader();
        try (WebSocketClassLoader cl = new WebSocketClassLoader(wsUrl(), platformOnly)) {
            Class<?> clazz = cl.loadClass(DUMMY_CLASS_NAME, true);
            assertNotNull(clazz);
            assertEquals(DUMMY_CLASS_NAME, clazz.getName());
            assertSame(cl, clazz.getClassLoader(),
                    "The class must be loaded by WebSocketClassLoader, not a parent");
        }
    }

    @Test
    void loadClassThrowsClassNotFoundForMissingClass() throws Exception {
        ClassLoader platformOnly = ClassLoader.getPlatformClassLoader();
        try (WebSocketClassLoader cl = new WebSocketClassLoader(wsUrl(), platformOnly)) {
            assertThrows(ClassNotFoundException.class,
                    () -> cl.loadClass("com.nonexistent.DoesNotExist", true));
        }
    }

    @Test
    void loadMultipleClassesConcurrently() throws Exception {
        // Generate additional dummy classes
        String[] classNames = {
                "integration.test.ConcA",
                "integration.test.ConcB",
                "integration.test.ConcC",
                "integration.test.ConcD",
        };
        for (String name : classNames) {
            generateDummyClass(tempDir, name);
        }

        ClassLoader platformOnly = ClassLoader.getPlatformClassLoader();
        try (WebSocketClassLoader cl = new WebSocketClassLoader(wsUrl(), platformOnly)) {
            Thread[] threads = new Thread[classNames.length];
            Class<?>[] results = new Class<?>[classNames.length];
            Throwable[] errors = new Throwable[classNames.length];

            for (int i = 0; i < classNames.length; i++) {
                final int idx = i;
                threads[i] = new Thread(() -> {
                    try {
                        results[idx] = cl.loadClass(classNames[idx], true);
                    } catch (Throwable t) {
                        errors[idx] = t;
                    }
                });
                threads[i].start();
            }

            for (Thread t : threads) {
                t.join(10_000);
            }

            for (int i = 0; i < classNames.length; i++) {
                assertNull(errors[i], "Loading " + classNames[i] + " failed: " + errors[i]);
                assertNotNull(results[i], "Class " + classNames[i] + " should have been loaded");
                assertEquals(classNames[i], results[i].getName());
                assertSame(cl, results[i].getClassLoader());
            }
        }
    }

    /**
     * Generates a minimal valid .class file for the given fully-qualified class name
     * under the given root directory.
     *
     * The generated class is equivalent to:
     * <pre>
     *   package integration.test;
     *   public class HelloDummy extends java.lang.Object {}
     * </pre>
     *
     * The bytecode is constructed manually (no compiler/ASM needed).
     */
    private static void generateDummyClass(Path root, String fqcn) throws IOException {
        String internalName = fqcn.replace('.', '/');
        String superName = "java/lang/Object";

        // Build constant pool
        byte[][] pool = new byte[16][];
        int idx = 1;

        // #1 CONSTANT_Class -> #2
        int thisClassIdx = idx;
        pool[idx++] = new byte[]{ 7, 0, (byte)(idx) }; // points to #2

        // #2 CONSTANT_Utf8 thisClass internal name
        int thisNameIdx = idx;
        pool[idx++] = utf8Entry(internalName);

        // #3 CONSTANT_Class -> #4
        int superClassIdx = idx;
        pool[idx++] = new byte[]{ 7, 0, (byte)(idx) }; // points to #4

        // #4 CONSTANT_Utf8 superClass internal name
        pool[idx++] = utf8Entry(superName);

        // #5 CONSTANT_Utf8 "<init>"
        int initNameIdx = idx;
        pool[idx++] = utf8Entry("<init>");

        // #6 CONSTANT_Utf8 "()V"
        int initDescIdx = idx;
        pool[idx++] = utf8Entry("()V");

        // #7 CONSTANT_Utf8 "Code"
        int codeIdx = idx;
        pool[idx++] = utf8Entry("Code");

        // #8 CONSTANT_NameAndType -> #5, #6
        int natIdx = idx;
        pool[idx++] = new byte[]{ 12, 0, (byte)initNameIdx, 0, (byte)initDescIdx };

        // #9 CONSTANT_Methodref -> #3 (super class), #8
        int methodRefIdx = idx;
        pool[idx++] = new byte[]{ 10, 0, (byte)superClassIdx, 0, (byte)natIdx };

        int poolCount = idx;

        // Build class file
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

        // Magic
        out.write(new byte[]{ (byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE });
        // Version: Java 8 (52.0) for maximum compatibility
        writeU2(out, 0); // minor
        writeU2(out, 52); // major
        // Constant pool count
        writeU2(out, poolCount);
        for (int i = 1; i < poolCount; i++) {
            out.write(pool[i]);
        }
        // Access flags: ACC_PUBLIC | ACC_SUPER
        writeU2(out, 0x0021);
        // This class
        writeU2(out, thisClassIdx);
        // Super class
        writeU2(out, superClassIdx);
        // Interfaces count
        writeU2(out, 0);
        // Fields count
        writeU2(out, 0);
        // Methods count: 1 (default constructor)
        writeU2(out, 1);

        // Method: <init>()V
        writeU2(out, 0x0001); // ACC_PUBLIC
        writeU2(out, initNameIdx);
        writeU2(out, initDescIdx);
        writeU2(out, 1); // attributes_count = 1 (Code)

        // Code attribute
        writeU2(out, codeIdx); // attribute_name_index
        byte[] code = {
                0x2A,             // aload_0
                (byte)0xB7, 0, (byte)methodRefIdx, // invokespecial #methodRefIdx
                (byte)0xB1        // return
        };
        int codeAttrLength = 2 + 2 + 4 + code.length + 2 + 2; // max_stack + max_locals + code_length + code + exception_table_length + attributes_count
        writeU4(out, codeAttrLength);
        writeU2(out, 1); // max_stack
        writeU2(out, 1); // max_locals
        writeU4(out, code.length);
        out.write(code);
        writeU2(out, 0); // exception_table_length
        writeU2(out, 0); // attributes_count (of Code)

        // Class attributes count
        writeU2(out, 0);

        // Write to file
        Path classFile = root.resolve(internalName + ".class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, out.toByteArray());
    }

    private static byte[] utf8Entry(String s) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] entry = new byte[3 + bytes.length];
        entry[0] = 1; // CONSTANT_Utf8
        entry[1] = (byte)(bytes.length >> 8);
        entry[2] = (byte)(bytes.length);
        System.arraycopy(bytes, 0, entry, 3, bytes.length);
        return entry;
    }

    private static void writeU2(java.io.OutputStream out, int value) throws IOException {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeU4(java.io.OutputStream out, int value) throws IOException {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}

package net.unit8.wscl.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for PropertyUtils
 *
 * @author kawasima
 */
public class PropertyUtilsTest {
    @BeforeEach
    public void setUp() {
        System.setProperty("wscl.home", "/hoge/fuga");
        System.setProperty("wscl.home2", "${wscl.home}/piyo");
    }

    @Test
    public void test() {
        assertEquals(new File("/hoge/fuga/piyo"),
                PropertyUtils.getFileSystemProperty("wscl.home2"));
    }

    @AfterEach
    public void tearDown() {
        System.clearProperty("wscl.home");
        System.clearProperty("wscl.home2");
    }
}

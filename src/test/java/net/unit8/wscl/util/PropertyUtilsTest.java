package net.unit8.wscl.util;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

/**
 * Tests for PropertyUtils
 *
 * @author kawasima
 */
public class PropertyUtilsTest {
    @Before
    public void setUp() {
        System.setProperty("wscl.home", "/hoge/fuga");
        System.setProperty("wscl.home2", "${wscl.home}/piyo");
    }
    @Test
    public void test() {
        Assert.assertEquals(new File("/hoge/fuga/piyo"),
                PropertyUtils.getFileSystemProperty("wscl.home2"));
    }

    @After
    public void tearDown() {
        System.clearProperty("wscl.home");
        System.clearProperty("wscl.home2");
    }

}

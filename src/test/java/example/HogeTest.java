package example;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by tie199026 on 14/03/06.
 */
public class HogeTest {
    @Test
    public void fuga() {
        System.out.println("huga-----!!!");
    }

    @Test
    public void piyo() {
        Assert.assertThat("piyo", CoreMatchers.is("PIYO"));
    }

    @Test
    public void raiseException() {
        throw new RuntimeException("Unknown Exception");
    }
}

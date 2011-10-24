import hudson.util.VersionNumber;
import junit.framework.TestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class DummyTest extends TestCase {
    public void test1() {} // work around a bug in surefire plugin

    public void test2() {
        assertTrue(new VersionNumber("1.0-alpha-1").compareTo(new VersionNumber("1.0"))<0);
    }
}

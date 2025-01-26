package io.jenkins.update_center;

import org.junit.Test;
import org.owasp.html.HtmlSanitizer;
import org.owasp.html.HtmlStreamRenderer;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SanitizerTest {
    private static final Logger LOGGER = Logger.getLogger(SanitizerTest.class.getName());

    @Test
    public void testSanitizer() {
        assertSanitize("<strong>strong!</strong>", "<strong>strong!</strong>");
        assertSanitize("foo", "foo<img src=x onerror=alert(1)>");
        assertSanitize("this is the logo:", "this is the logo:<img src='https://www.jenkins.io/images/gsoc/jenkins-gsoc-transparent.png'>");
        assertSanitize("this is the <a href=\"https://jenkins.io\" target=\"_blank\" rel=\"nofollow noreferrer noopener\">URL</a>", "this is the <a href=\"https://jenkins.io\" target=\"_blank\">URL</a>");
        assertSanitize("this is the <a href=\"https://jenkins.io\" target=\"_blank\" rel=\"nofollow noreferrer noopener\">URL</a>", "this is the <a href=\"https://jenkins.io\">URL</a>");
        assertSanitize("this is the <a href=\"https://jenkins.io\" target=\"_blank\" rel=\"nofollow noreferrer noopener\">URL</a>", "this is the <a href=\"https://jenkins.io\" target=\"foo\">URL</a>");
        assertSanitize("this is the <a href=\"https://jenkins.io\" target=\"_blank\" rel=\"nofollow noreferrer noopener\">URL</a>", "this is the <a target=\"____\" href=\"https://jenkins.io\">URL</a>");
    }

    private void assertSanitize(String expected, String input) {
        StringBuilder b = new StringBuilder();
        HtmlStreamRenderer renderer = HtmlStreamRenderer.create(b, Throwable::printStackTrace, html -> LOGGER.log(Level.INFO, "Bad HTML: '" + html + "'"));
        HtmlSanitizer.sanitize(input, HPI.HTML_POLICY.apply(renderer), HPI.PRE_PROCESSOR);
        assertSanitizer336Workaround(expected, b.toString());
    }

    // Workaround for https://github.com/OWASP/java-html-sanitizer/issues/336
    private void assertSanitizer336Workaround(String expected, String actual) {
        if (expected.contains("rel")) {
            assertThat(expected, allOf(containsString("nofollow"), containsString("noopener"), containsString("noreferrer")));
            assertThat(actual, allOf(containsString("nofollow"), containsString("noopener"), containsString("noreferrer")));
        }
        expected = expected.replace("nofollow", "*").replace("noopener", "*").replace("noreferrer", "*");
        actual = actual.replace("nofollow", "*").replace("noopener", "*").replace("noreferrer", "*");
        assertThat(expected, is(actual));
    }
}

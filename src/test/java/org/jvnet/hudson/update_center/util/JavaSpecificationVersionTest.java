/*
 * The MIT License
 *
 * Copyright (c) 2018 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jvnet.hudson.update_center.util;

import org.junit.Assert;
import org.junit.Test;
import static org.jvnet.hudson.update_center.util.JavaSpecificationVersion.*;

/**
 * Tests for {@link JavaSpecificationVersion}.
 */
public class JavaSpecificationVersionTest {

    @Test
    public void shouldParseValidNumbersCorrectly() {
        assertSpecEquals(JAVA_6, "1.6");
        assertSpecEquals(JAVA_7, "1.7");
        assertSpecEquals(JAVA_8, "1.8");
        assertSpecEquals(JAVA_9, "9");
        assertSpecEquals(JAVA_10, "10");
        assertSpecEquals(JAVA_11, "11");
    }

    @Test
    public void shouldParseOldSpecCorrectly() {
        assertSpecEquals(JAVA_9, "1.9");
        assertSpecEquals(JAVA_10, "1.10");
        assertSpecEquals(JAVA_11, "1.11");
        assertSpecEquals(JAVA_12, "1.12");
    }

    @Test
    public void shouldResolveIncorrectSpecs() {
        assertSpecEquals(JAVA_8, "8");
        assertSpecEquals(JAVA_7, "7");
        assertSpecEquals(JAVA_5, "5");
    }

    @Test
    public void shouldCompareVersionsProperly() {
        Assert.assertTrue(JAVA_5.isOlderThan(JAVA_6));
        Assert.assertTrue(JAVA_6.isOlderThan(JAVA_7));
        Assert.assertTrue(JAVA_7.isOlderThan(JAVA_8));
        Assert.assertTrue(JAVA_8.isOlderThan(JAVA_9));
        Assert.assertTrue(JAVA_8.isNewerThan(JAVA_7));
        Assert.assertTrue(JAVA_9.isOlderThan(JAVA_10));
        Assert.assertTrue(JAVA_10.isOlderThan(JAVA_11));
        Assert.assertTrue(JAVA_10.isNewerThan(JAVA_8));
    }

    public void assertSpecEquals(JavaSpecificationVersion version, String value) {
        JavaSpecificationVersion actualSpec = new JavaSpecificationVersion(value);
        Assert.assertEquals("Wrong Java version", version, actualSpec);
    }

    public void assertOlder(JavaSpecificationVersion version1, JavaSpecificationVersion version2) {
        Assert.assertTrue(String.format("Version %s should be older than %s", version1, version2),
                version1.isOlderThan(version2));
    }

    public void assertNewer(JavaSpecificationVersion version1, JavaSpecificationVersion version2) {
        Assert.assertTrue(String.format("Version %s should be newer than %s", version1, version2),
                version1.isNewerThan(version2));
    }
}

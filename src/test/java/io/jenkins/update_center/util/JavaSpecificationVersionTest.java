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
package io.jenkins.update_center.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link JavaSpecificationVersion}.
 */
public class JavaSpecificationVersionTest {

    @Test
    public void shouldParseValidNumbersCorrectly() {
        assertSpecEquals(JavaSpecificationVersion.JAVA_6, "1.6");
        assertSpecEquals(JavaSpecificationVersion.JAVA_7, "1.7");
        assertSpecEquals(JavaSpecificationVersion.JAVA_8, "1.8");
        assertSpecEquals(JavaSpecificationVersion.JAVA_9, "9");
        assertSpecEquals(JavaSpecificationVersion.JAVA_10, "10");
        assertSpecEquals(JavaSpecificationVersion.JAVA_11, "11");
    }

    @Test
    public void shouldParseOldSpecCorrectly() {
        assertSpecEquals(JavaSpecificationVersion.JAVA_9, "1.9");
        assertSpecEquals(JavaSpecificationVersion.JAVA_10, "1.10");
        assertSpecEquals(JavaSpecificationVersion.JAVA_11, "1.11");
        assertSpecEquals(JavaSpecificationVersion.JAVA_12, "1.12");
    }

    @Test
    public void shouldResolveIncorrectSpecs() {
        assertSpecEquals(JavaSpecificationVersion.JAVA_8, "8");
        assertSpecEquals(JavaSpecificationVersion.JAVA_7, "7");
        assertSpecEquals(JavaSpecificationVersion.JAVA_5, "5");
    }

    @Test
    public void shouldCompareVersionsProperly() {
        Assert.assertTrue(JavaSpecificationVersion.JAVA_5.isOlderThan(JavaSpecificationVersion.JAVA_6));
        Assert.assertTrue(JavaSpecificationVersion.JAVA_6.isOlderThan(JavaSpecificationVersion.JAVA_7));
        Assert.assertTrue(JavaSpecificationVersion.JAVA_7.isOlderThan(JavaSpecificationVersion.JAVA_8));
        Assert.assertTrue(JavaSpecificationVersion.JAVA_8.isOlderThan(JavaSpecificationVersion.JAVA_9));
        Assert.assertTrue(JavaSpecificationVersion.JAVA_8.isNewerThan(JavaSpecificationVersion.JAVA_7));
        Assert.assertTrue(JavaSpecificationVersion.JAVA_9.isOlderThan(JavaSpecificationVersion.JAVA_10));
        Assert.assertTrue(JavaSpecificationVersion.JAVA_10.isOlderThan(JavaSpecificationVersion.JAVA_11));
        Assert.assertTrue(JavaSpecificationVersion.JAVA_10.isNewerThan(JavaSpecificationVersion.JAVA_8));
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

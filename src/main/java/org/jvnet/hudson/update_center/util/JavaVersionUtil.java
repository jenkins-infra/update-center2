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

import hudson.util.VersionNumber;

import javax.annotation.Nonnull;

/**
 * Utilities for Java version handling;
 * @author Oleg Nenashev
 * @since TODO
 */
public class JavaVersionUtil {

    public static final VersionNumber CORE_JAVA_7 = new VersionNumber("1.612");
    public static final VersionNumber CORE_JAVA_8 = new VersionNumber("2.54");

    private JavaVersionUtil() {
        // prohibited
    }

    /**
     * Guess Java version based on Jenkins core version requirements.
     * This method may return incorrect result for latest versions if Jenkins updates to the recent core.
     * @return Version used by the core
     */
    public static JavaSpecificationVersion interpolateJavaVersionByCore(@Nonnull VersionNumber jenkinsCoreVersion) {
        if (jenkinsCoreVersion.isOlderThan(CORE_JAVA_7)) {
            return JavaSpecificationVersion.JAVA_6;
        }
        if (jenkinsCoreVersion.isOlderThan(CORE_JAVA_8)) {
            return JavaSpecificationVersion.JAVA_7;
        }
        return JavaSpecificationVersion.JAVA_8;
    }
}

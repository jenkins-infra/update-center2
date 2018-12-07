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

//TODO: Move it to the VersionNumber lib

/**
 * Java Version Specification.
 * Implemented according to https://openjdk.java.net/jeps/223
 * @author Oleg Nenashev
 */
public class JavaSpecificationVersion extends VersionNumber {

    public static final JavaSpecificationVersion JAVA_5 = new JavaSpecificationVersion("1.5");
    public static final JavaSpecificationVersion JAVA_6 = new JavaSpecificationVersion("1.6");
    public static final JavaSpecificationVersion JAVA_7 = new JavaSpecificationVersion("1.7");
    public static final JavaSpecificationVersion JAVA_8 = new JavaSpecificationVersion("1.8");
    public static final JavaSpecificationVersion JAVA_9 = new JavaSpecificationVersion("9");
    public static final JavaSpecificationVersion JAVA_10 = new JavaSpecificationVersion("10");
    public static final JavaSpecificationVersion JAVA_11 = new JavaSpecificationVersion("11");
    public static final JavaSpecificationVersion JAVA_12 = new JavaSpecificationVersion("12");

    /**
     * Constructor which automatically normalizes version strings.
     * @param version Java specification version, should follow JEP-223 or the previous format.
     * @throws NumberFormatException Illegal Java specification version number
     */
    public JavaSpecificationVersion(@Nonnull String version)
            throws NumberFormatException {
        super(normalizeVersion(version));
    }

    @Nonnull
    private static String normalizeVersion(@Nonnull String input)
            throws NumberFormatException {
        input = input.trim();
        if (input.startsWith("1.")) {
            String[] split = input.split("\\.");
            if (split.length != 2) {
                throw new NumberFormatException("Old java.specification.version: There should be exactly one dot and something after it");
            }
            input = split[1];
        }

        int majorVersion = Integer.parseInt(input);
        if (majorVersion > 8) {
            return input;
        } else {
            return "1." + input;
        }
    }


}

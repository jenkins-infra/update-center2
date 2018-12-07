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
package org.jvnet.hudson.update_center.impl.pluginFilter;

import hudson.util.VersionNumber;
import org.jvnet.hudson.update_center.HPI;
import org.jvnet.hudson.update_center.PluginFilter;
import org.jvnet.hudson.update_center.util.JavaSpecificationVersion;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Oleg Nenashev
 */
public class JavaVersionPluginFilter implements PluginFilter {

    private static final Logger LOGGER = Logger.getLogger(JavaVersionPluginFilter.class.getName());

    /**
     * Java version, with which the plugin should be compatible.
     */
    @Nonnull
    private final VersionNumber javaVersion;

    public JavaVersionPluginFilter(@Nonnull JavaSpecificationVersion javaVersion) {
        this.javaVersion = javaVersion;
    }

    @Override
    public boolean shouldIgnore(@Nonnull HPI hpi) {
        final JavaSpecificationVersion pluginJavaVersion;
        try {
            pluginJavaVersion = hpi.getMinimumJavaVersion();
        } catch (IOException e) {
            LOGGER.log(Level.FINE,
                    String.format("Minimum Java Version cannot be determined for %s, will accept it", hpi),
                    e);
            return false;
        }
        if (pluginJavaVersion == null) {
            LOGGER.log(Level.FINE, "Minimum Java Version cannot be determined for {0}, will accept it", hpi);
            return false;
        }

        if (javaVersion.isOlderThan(pluginJavaVersion)) {
            LOGGER.log(Level.INFO, "Ignoring {0}:{1}. Java version {2} is required, but it is newer than the target version {3}",
                    new Object[] {hpi.artifact.artifactId, hpi.getVersion(), pluginJavaVersion, javaVersion});
            return true;
        }
        LOGGER.log(Level.FINEST, "Accepting {0}:{1}. It requires Java version {2}, which is compliant with {3}",
                new Object[] {hpi.artifact.artifactId, hpi.getVersion(), pluginJavaVersion, javaVersion});
        return false;
    }
}

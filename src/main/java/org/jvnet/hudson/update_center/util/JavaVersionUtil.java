package org.jvnet.hudson.update_center.util;

import hudson.util.VersionNumber;

import javax.annotation.Nonnull;

/**
 * Utilities for Java version handling;
 * @author Oleg Nenashev
 * @since TODO
 */
public class JavaVersionUtil {

    public static final VersionNumber JAVA_6 = new VersionNumber("1.6");
    public static final VersionNumber JAVA_7 = new VersionNumber("1.7");
    public static final VersionNumber JAVA_8 = new VersionNumber("1.8");
    public static final VersionNumber JAVA_11 = new VersionNumber("11");

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
    public static VersionNumber interpolateJavaVersionByCore(@Nonnull VersionNumber jenkinsCoreVersion) {
        if (jenkinsCoreVersion.isOlderThan(CORE_JAVA_7)) {
            return JAVA_6;
        }
        if (jenkinsCoreVersion.isOlderThan(CORE_JAVA_8)) {
            return JAVA_7;
        }
        return JAVA_8;
    }
}

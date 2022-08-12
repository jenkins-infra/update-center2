package io.jenkins.update_center.args4j;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Provide a way to define default values for {@link org.kohsuke.args4j.Option}s
 * that cannot be reset in {@link io.jenkins.update_center.Main#run} when taking
 * an arguments file.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Default {
    String value();
}

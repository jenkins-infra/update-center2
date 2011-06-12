package org.jvnet.hudson.update_center;

import com.sun.source.util.JavacTask;

import javax.lang.model.element.TypeElement;

/**
 * Information about the implementation of an extension point in a plugin.
 *
 * @author Kohsuke Kawaguchi
 */
public final class ExtensionImpl {
    /**
     * Back reference to the plugin where this implementation was found.
     */
    public final HPI plugin;

    /**
     * The compiler session where this information was determined.
     */
    public final JavacTask javac;

    /**
     * Type that implements the extension point.
     */
    public final TypeElement implementation;

    /**
     * Type that represents the extension point itself
     * (from which {@link #implementation} derives from.)
     */
    public final TypeElement extensionPoint;

    ExtensionImpl(HPI plugin, JavacTask javac, TypeElement implementation, TypeElement extensionPoint) {
        this.plugin = plugin;
        this.javac = javac;
        this.implementation = implementation;
        this.extensionPoint = extensionPoint;
    }
}

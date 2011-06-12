package org.jvnet.hudson.update_center;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import net.sf.json.JSONObject;

import javax.lang.model.element.TypeElement;
import java.io.File;

/**
 * Information about the implementation of an extension point
 * (and extension point definition.)
 *
 * @author Kohsuke Kawaguchi
 */
public final class Extension {
    /**
     * Back reference to the artifact where this implementation was found.
     */
    public final MavenArtifact artifact;

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

    /**
     * {@link TreePath} that leads to {@link #implementation}
     */
    public final TreePath implPath;

    /**
     * {@link Trees} object for {@link #javac}
     */
    public final Trees trees;

    Extension(MavenArtifact artifact, JavacTask javac, Trees trees, TypeElement implementation, TreePath implPath, TypeElement extensionPoint) {
        this.artifact = artifact;
        this.javac = javac;
        this.implementation = implementation;
        this.implPath = implPath;
        this.extensionPoint = extensionPoint;
        this.trees = trees;
    }

    /**
     * Returns true if this record is about a definition of an extension point
     * (as opposed to an implementation of a defined extension point.)
     */
    public boolean isDefinition() {
        return implementation.equals(extensionPoint);
    }

    /**
     * Returns the {@link ClassTree} representation of {@link #implementation}.
     */
    public ClassTree getClassTree() {
        return (ClassTree)implPath.getLeaf();
    }

    public CompilationUnitTree getCompilationUnit() {
        return implPath.getCompilationUnit();
    }

    /**
     * Gets the source file name that contains this definition, including directories
     * that match the package name portion.
     */
    public String getSourceFile() {
        ExpressionTree packageName = getCompilationUnit().getPackageName();
        String pkg = packageName==null?"":packageName.toString().replace('.', '/')+'/';

        String name = new File(getCompilationUnit().getSourceFile().getName()).getName();
        return pkg + name;
    }

    /**
     * Gets the line number in the source file where this implementation was defined.
     */
    public long getLineNumber() {
        return getCompilationUnit().getLineMap().getLineNumber(
                trees.getSourcePositions().getStartPosition(getCompilationUnit(), getClassTree()));
    }

    public String getJavadoc() {
        return javac.getElements().getDocComment(implementation);
    }

    /**
     * Returns the artifact Id of the plugin that it came from.
     */
    public String getArtifactId() {
        return artifact.artifact.artifactId;
    }

    public JSONObject toJSON() {
        JSONObject i = new JSONObject();
        i.put("className",implementation.getQualifiedName().toString());
        i.put("artifact",artifact.getGavId());
        i.put("javadoc",getJavadoc());
        i.put("sourceFile",getSourceFile());
        i.put("lineNumber",getLineNumber());
        return i;
    }
}

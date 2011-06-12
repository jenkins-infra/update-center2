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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * Javadoc converted to the confluence markup.
     */
    public String getConfluenceDoc() {
        String javadoc = getJavadoc();
        if(javadoc==null)   return null;

        {// replace @link
            Matcher m = LINK.matcher(javadoc);
            StringBuffer sb = new StringBuffer();
            while(m.find()) {
                String simpleName = m.group(1);
                m.appendReplacement(sb, '['+simpleName+"@javadoc]");
            }
            m.appendTail(sb);
            javadoc = sb.toString();
        }

        for (Macro m : MACROS)
            javadoc = m.replace(javadoc);
        return javadoc;
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
        i.put("confluenceDoc", getConfluenceDoc());
        i.put("sourceFile",getSourceFile());
        i.put("lineNumber",getLineNumber());
        return i;
    }

    private static final class Macro {
        private final Pattern from;
        private final String to;

        private Macro(Pattern from, String to) {
            this.from = from;
            this.to = to;
        }

        public String replace(String s) {
            return from.matcher(s).replaceAll(to);
        }
    }

    private static final Pattern LINK = Pattern.compile("\\{@link ([^}]+)}");
    private static final Macro[] MACROS = new Macro[] {
        new Macro(LINK,     "{{$1{}}}"),
        new Macro(Pattern.compile("<tt>([^<]+?)</tt>"),  "{{$1{}}}"),
        new Macro(Pattern.compile("<b>([^<]+?)</b>"),  "*$1*"),
        new Macro(Pattern.compile("<p/?>"),  "\n"),
        new Macro(Pattern.compile("</p>"),  "")
    };
}

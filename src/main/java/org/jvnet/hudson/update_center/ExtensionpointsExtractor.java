package org.jvnet.hudson.update_center;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTool;
import org.apache.commons.io.IOUtils;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Finds the defined extension points in a HPI.
 *
 * @author Robert Sandell
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings({"Since15"})
public class ExtensionpointsExtractor {

    private MavenArtifact hpi;

    public ExtensionpointsExtractor(MavenArtifact hpi) {
        this.hpi = hpi;
    }

    public List<ExtensionImpl> extract() throws IOException, InterruptedException {
        File tempDir = File.createTempFile("jenkins","extPoint");
        tempDir.delete();
        tempDir.mkdirs();

        StandardJavaFileManager fileManager = null;
        try {
            File srcdir = new File(tempDir,"src");
            File libdir = new File(tempDir,"lib");
            unzip(hpi.resolveSources(),srcdir);

            File pom = hpi.resolvePOM();
            FileUtils.copyFile(pom, new File(srcdir, "pom.xml"));
            downloadDependencies(srcdir,libdir);

            JavaCompiler javac1 = JavacTool.create();
            DiagnosticListener<? super JavaFileObject> errorListener = new DiagnosticListener<JavaFileObject>() {
                public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
                    //TODO report
                    System.out.println(diagnostic);
                }
            };
            fileManager = javac1.getStandardFileManager(errorListener, Locale.getDefault(), Charset.defaultCharset());


            fileManager.setLocation(StandardLocation.CLASS_PATH, generateClassPath(libdir));

            // annotation processing appears to cause the source files to be reparsed
            // (even though I couldn't find exactly where it's done), which causes
            // Tree symbols created by the original JavacTask.parse() call to be thrown away,
            // which breaks later processing.
            // So for now, don't perform annotation processing
            List<String> options = Arrays.asList("-proc:none");

            Iterable<? extends JavaFileObject> files = fileManager.getJavaFileObjectsFromFiles(generateSources(srcdir));
            JavaCompiler.CompilationTask task = javac1.getTask(null, fileManager, errorListener, options, null, files);
            final JavacTask javac = (JavacTask) task;
            final Trees trees = Trees.instance(javac);
            final Elements elements = javac.getElements();
            final Types types = javac.getTypes();

            Iterable<? extends CompilationUnitTree> parsed = javac.parse();
            javac.analyze();

            final List<ExtensionImpl> r = new ArrayList<ExtensionImpl>();

            // discover all compiled types
            TreePathScanner<?,?> classScanner = new TreePathScanner<Void,Void>() {
                final TypeElement extensionPoint = elements.getTypeElement("hudson.ExtensionPoint");

                public Void visitClass(ClassTree ct, Void _) {
                    TreePath path = getCurrentPath();
                    TypeElement e = (TypeElement) trees.getElement(path);
                    if(e!=null)
                        checkIfExtension(path,e,e);

                    return super.visitClass(ct, _);
                }

                private void checkIfExtension(TreePath pathToRoot, TypeElement root, TypeElement e) {
                    for (TypeMirror i : e.getInterfaces()) {
                        if (types.asElement(i).equals(extensionPoint))
                            r.add(new ExtensionImpl(hpi, javac, trees, root, pathToRoot, e));
                        checkIfExtension(pathToRoot,root,(TypeElement)types.asElement(i));
                    }
                    TypeMirror s = e.getSuperclass();
                    if (!(s instanceof NoType))
                        checkIfExtension(pathToRoot,root,(TypeElement)types.asElement(s));
                }
            };

            for( CompilationUnitTree u : parsed )
                classScanner.scan(u,null);

            return r;
        } finally {
            FileUtils.deleteDirectory(tempDir);
            if (fileManager!=null)
                fileManager.close();
        }
    }

    private Iterable<? extends File> generateClassPath(File dependencies) {
        return FileUtils.getFileIterator(dependencies, "jar");
    }

    private Iterable<? extends File> generateSources(File sourceDir) {
        return FileUtils.getFileIterator(sourceDir, "java");

    }

    private void downloadDependencies(File pomDir, File destDir) throws IOException, InterruptedException {
        destDir.mkdirs();
        ProcessBuilder builder = new ProcessBuilder("mvn",
                "dependency:copy-dependencies",
                "-DincludeScope=compile",
                "-DoutputDirectory=" + destDir.getAbsolutePath());
        builder.directory(pomDir);
        builder.redirectErrorStream(true);
        Process proc = builder.start();

        // capture the output, but only report it in case of an error
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        proc.getOutputStream().close();
        IOUtils.copy(proc.getInputStream(),output);
        proc.getErrorStream().close();
        proc.getInputStream().close();

        int result = proc.waitFor();
        if (result != 0) {
            System.out.write(output.toByteArray());
            throw new IOException("Maven didn't like this! " + pomDir.getAbsolutePath());
        }
    }

    private void unzip(File sourcesJar, File destDir) throws IOException {
        FileUtils.unzip(sourcesJar, destDir);
    }

}

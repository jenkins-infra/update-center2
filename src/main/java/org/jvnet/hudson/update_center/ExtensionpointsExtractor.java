package org.jvnet.hudson.update_center;

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;
import net.sf.json.JSONObject;
import org.jaxen.function.ext.EndsWithFunction;

import javax.tools.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Finds the defined extension points in a HPI.
 */
public class ExtensionpointsExtractor {

    private PluginHistory hpi;

    public ExtensionpointsExtractor(PluginHistory hpi) {
        this.hpi = hpi;
    }

    public JSONObject extract() throws IOException, InterruptedException {
        HPI latest = hpi.latest();
        File sourcesJar = latest.resolveSources();
        File dir = unzip(sourcesJar);
        File pom = latest.resolvePOM();
        FileUtils.copyInto(pom, dir, "pom.xml");
        File dependencies = downloadDependencies(dir);
        //mvn dependency:copy-dependencies -DincludeScope=compile -DoutputDirectory=demo
        //http://pastebin.com/ejmfhxeL
        JavacTask javac = prepareJavac(dir, dependencies);
        return new JSONObject();
    }

    private JavacTask prepareJavac(File sourceFiles, File dependencies) throws IOException {
        JavaCompiler javac = JavacTool.create();
        DiagnosticListener<? super JavaFileObject> errorListener = new DiagnosticListener<JavaFileObject>() {
            public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
                //TODO report
            }
        };
        StandardJavaFileManager fileManager = javac.getStandardFileManager(errorListener, Locale.getDefault(), Charset.defaultCharset());


        fileManager.setLocation(StandardLocation.CLASS_PATH, generateClassPath(dependencies));

        // annotation processing appears to cause the source files to be reparsed
        // (even though I couldn't find exactly where it's done), which causes
        // Tree symbols created by the original JavacTask.parse() call to be thrown away,
        // which breaks later processing.
        // So for now, don't perform annotation processing
        List<String> options = Arrays.asList("-proc:none");

        Iterable<? extends JavaFileObject> files = fileManager.getJavaFileObjectsFromFiles(generateSources(sourceFiles));
        JavaCompiler.CompilationTask task = javac.getTask(null, fileManager, errorListener, options, null, files);
        return (JavacTask) task;
    }

    private Iterable<? extends File> generateClassPath(File dependencies) {
        return FileUtils.getFileIterator(dependencies, ".jar");
    }

    private Iterable<? extends File> generateSources(File sourceDir) {
        return FileUtils.getFileIterator(sourceDir, ".java");

    }

    private File downloadDependencies(File pomDir) throws IOException, InterruptedException {
        File tempDir = new File(System.getProperty("java.io.tmp"), hpi.artifactId + "-dependencies-" + System.currentTimeMillis());
        tempDir.mkdirs();
        tempDir.deleteOnExit();
        ProcessBuilder builder = new ProcessBuilder("mvn",
                "dependency:copy-dependencies",
                "-DincludeScope=compile",
                "-DoutputDirectory=" + tempDir.getAbsolutePath());
        builder.directory(pomDir);
        int result = builder.start().waitFor();
        if (result != 0) {
            throw new IOException("Maven didn't like this! " + pomDir.getAbsolutePath());
        }
        return tempDir;
    }

    private File unzip(File sourcesJar) throws IOException {
        File tempDir = new File(System.getProperty("java.io.tmp"), hpi.artifactId + "-source-" + System.currentTimeMillis());
        tempDir.mkdirs();
        tempDir.deleteOnExit();
        FileUtils.unzip(sourcesJar, tempDir);
        return tempDir;
    }
}

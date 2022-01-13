/*
 * The MIT License
 *
 * Copyright (c) 2004-2020, Sun Microsystems, Inc. and other contributors
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
package io.jenkins.update_center;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.util.VersionNumber;
import io.jenkins.lib.support_log_formatter.SupportLogFormatter;
import io.jenkins.update_center.args4j.LevelOptionHandler;
import io.jenkins.update_center.json.PlatformPluginsRoot;
import io.jenkins.update_center.json.RecentReleasesRoot;
import io.jenkins.update_center.json.TieredUpdateSitesGenerator;
import io.jenkins.update_center.json.PluginDocumentationUrlsRoot;
import io.jenkins.update_center.wrappers.AlphaBetaOnlyRepository;
import io.jenkins.update_center.wrappers.StableWarMavenRepository;
import io.jenkins.update_center.wrappers.VersionCappedMavenRepository;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import io.jenkins.update_center.filters.JavaVersionPluginFilter;
import io.jenkins.update_center.json.PluginVersionsRoot;
import io.jenkins.update_center.json.ReleaseHistoryRoot;
import io.jenkins.update_center.json.UpdateCenterRoot;
import io.jenkins.update_center.util.JavaSpecificationVersion;
import io.jenkins.update_center.wrappers.FilteringRepository;
import io.jenkins.update_center.wrappers.TruncatedMavenRepository;
import io.jenkins.update_center.wrappers.AllowedArtifactsListMavenRepository;
import org.kohsuke.args4j.ClassParser;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import javax.annotation.CheckForNull;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    /* Control meta-execution options */
    @Option(name = "--arguments-file", usage = "Specify invocation arguments in a file, with each line being a separate update site build. This argument cannot be re-set via arguments-file.")
    @SuppressFBWarnings
    @CheckForNull public static File argumentsFile;

    @Option(name = "--resources-dir", usage = "Specify the path to the resources directory containing warnings.json, artifact-ignores.properties, etc. This argument cannot be re-set via arguments-file.")
    @SuppressFBWarnings
    @NonNull public static File resourcesDir = new File("resources"); // Default value for tests -- TODO find a better way to set a value for tests

    @Option(name = "--log-level", usage = "A java.util.logging.Level name. Use CONFIG, FINE, FINER, or FINEST to log more output.", handler = LevelOptionHandler.class)
    @SuppressFBWarnings
    @CheckForNull public static Level level = Level.INFO;


    /* Configure repository source */
    @Option(name = "--limit-plugin-core-dependency", usage = "Cap the core dependency and only include plugins that are compatible with this core (or older)")
    @CheckForNull public String capPlugin;

    @Option(name = "--limit-core-release", usage = "Cap the version number of Jenkins core offered. Not generally useful.")
    @CheckForNull public String capCore; // TODO remove

    @Option(name = "--only-stable-core", usage = "Limit core releases to stable (LTS) releases (those with three component version numbers)")
    public boolean stableCore;

    @Option(name = "--only-experimental", usage = "Only include experimental alpha/beta releases")
    public boolean onlyExperimental; // TODO would it make more sense to generate this as the experimental update site?

    @Option(name = "--with-experimental", usage = "Include experimental alpha/beta releases")
    public boolean includeExperimental;

    @Option(name = "--java-version", usage = "Target Java version for the update center. Plugins will be excluded if their minimum Java version does not match. If not set, required Java version will be ignored")
    @CheckForNull public String javaVersion;

    @Option(name = "--max-plugins", usage = "For testing purposes: Limit the number of plugins included to the specified number.")
    @CheckForNull public Integer maxPlugins;

    @Option(name = "--allowed-artifacts-file", usage = "For testing purposes: A Java properties file whose keys are artifactIds and values are space separated lists of versions to allow, or '*' to allow all")
    @CheckForNull public File allowedArtifactsListFile;


    /* Configure what kinds of output to generate */
    @Option(name = "--dynamic-tier-list-file", usage = "Generate tier list JSON file at the specified path. If this option is set, we skip generating all other output.")
    @CheckForNull public File tierListFile;

    @Option(name = "--www-dir", usage = "Generate simple output files, JSON(ish) and others, into this directory")
    @CheckForNull public File www;

    @Option(name = "--skip-update-center", usage = "Skip generation of update center files (mostly useful during development)")
    public boolean skipUpdateCenter;

    @Option(name = "--skip-latest-plugin-release", usage = "Do not include information about the latest existing plugin release (if an older release is being offered)")
    public boolean skipLatestPluginRelease;

    @Option(name = "--generate-release-history", usage = "Generate release history")
    public boolean generateReleaseHistory;

    @Option(name = "--generate-plugin-versions", usage = "Generate plugin versions")
    public boolean generatePluginVersions;

    @Option(name = "--generate-plugin-documentation-urls", usage = "Generate plugin documentation URL mapping (for plugins.jenkins.io)")
    public boolean generatePluginDocumentationUrls;

    @Option(name = "--generate-recent-releases", usage = "Generate recent releases file (as input to targeted rsync etc.)")
    public boolean generateRecentReleases;

    @Option(name = "--generate-platform-plugins", usage = "Generate platform-plugins.json (to override wizard suggestions)")
    public boolean generatePlatformPlugins;


    /* Configure options modifying output */
    @Option(name = "--pretty-json", usage = "Pretty-print JSON files")
    public boolean prettyPrint;

    @Option(name = "--id", usage = "Uniquely identifies this update center. We recommend you use a dot-separated name like \"com.sun.wts.jenkins\". This value is not exposed to users, but instead internally used by Jenkins.")
    @CheckForNull public String id = "default";

    @Option(name = "--connection-check-url", usage = "Specify an URL of the 'always up' server for performing connection check.")
    @CheckForNull public String connectionCheckUrl = "https://www.google.com/";


    /* These fields are other objects configurable with command-line options */
    private Signer signer = new Signer();
    private MetadataWriter metadataWriter = new MetadataWriter();
    private DirectoryTreeBuilder directoryTreeBuilder = new DirectoryTreeBuilder();


    public static void main(String[] args) throws Exception {
        if (!System.getProperty("file.encoding").equals("UTF-8")) {
            System.err.println("This tool must be launched with -Dfile.encoding=UTF-8");
            System.exit(1);
        }

        final Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.INFO);
        for (Handler h : rootLogger.getHandlers()) {
            if (h instanceof ConsoleHandler) {
                h.setFormatter(new SupportLogFormatter());
            }
            h.setLevel(Level.ALL);
        }

        System.exit(new Main().run(args));
    }

    public int run(String[] args) throws Exception {
        CmdLineParser p = new CmdLineParser(this);
        new ClassParser().parse(signer, p);
        new ClassParser().parse(metadataWriter, p);
        new ClassParser().parse(directoryTreeBuilder, p);
        try {
            p.parseArgument(args);

            if (argumentsFile == null) {
                run();
            } else {
                List<String> invocations = IOUtils.readLines(Files.newBufferedReader(argumentsFile.toPath(), StandardCharsets.UTF_8));
                int executions = 0;
                for (String line : invocations) {
                    if (!line.trim().startsWith("#") && !line.trim().isEmpty()) { // TODO more flexible comments support, e.g. end-of-line

                        LOGGER.log(Level.INFO, "Running with args: " + line);
                        // TODO combine args array and this list
                        String[] invocationArgs = line.trim().split(" +");

                        resetArguments(this, signer, metadataWriter, directoryTreeBuilder);

                        p.parseArgument(invocationArgs);
                        run();
                        executions++;
                    }
                }
                LOGGER.log(Level.INFO, "Finished " + executions + " executions found in parameters file " + argumentsFile);
            }

            return 0;
        } catch (CmdLineException e) {
            LOGGER.log(Level.SEVERE, e.getMessage());
            p.printUsage(System.err);
            return 1;
        }
    }

    private void resetArguments(Object... optionHolders) {
        for (Object o : optionHolders) {
            for (Field field : o.getClass().getFields()) {
                if (field.getAnnotation(Option.class) != null && !Modifier.isStatic(field.getModifiers())) {
                    if (Object.class.isAssignableFrom(field.getType())) {
                        try {
                            field.set(o, null);
                        } catch (IllegalAccessException e) {
                            LOGGER.log(Level.WARNING, "Failed to reset argument", e);
                        }
                    } else if (boolean.class.isAssignableFrom(field.getType())) {
                        try {
                            field.set(o, false);
                        } catch (IllegalAccessException e) {
                            LOGGER.log(Level.WARNING, "Failed to reset boolean option", e);
                        }
                    }
                }
            }
        }
    }

    public void run() throws Exception {

        if (level != null) {
            PACKAGE_LOGGER.setLevel(level);
        }

        MavenRepository repo = createRepository();
        initializeLatestPluginVersions(skipLatestPluginRelease);

        if (tierListFile != null) {
            new TieredUpdateSitesGenerator().withRepository(repo).write(tierListFile, prettyPrint);
            return;
        }

        metadataWriter.writeMetadataFiles(repo, www);

        if (!skipUpdateCenter) {
            if (StringUtils.isEmpty(id)) {
                throw new IllegalArgumentException("--id argument is empty");
            }
            if (StringUtils.isEmpty(connectionCheckUrl)) {
                throw new IllegalArgumentException("--connection-check-url argument is empty");
            }

            UpdateCenterRoot updateCenterRoot = new UpdateCenterRoot(repo, new File(Main.resourcesDir, WARNINGS_JSON_FILENAME));
            updateCenterRoot.id = id;
            updateCenterRoot.connectionCheckUrl = connectionCheckUrl;

            final String signedUpdateCenterJson = updateCenterRoot.encodeWithSignature(signer, prettyPrint);
            writeToFile(updateCenterPostCallJson(signedUpdateCenterJson), new File(www, UPDATE_CENTER_JSON_FILENAME));
            writeToFile(signedUpdateCenterJson, new File(www, UPDATE_CENTER_ACTUAL_JSON_FILENAME));
            writeToFile(updateCenterPostMessageHtml(signedUpdateCenterJson), new File(www, UPDATE_CENTER_JSON_HTML_FILENAME));
        }

        if (generatePluginDocumentationUrls) {
            new PluginDocumentationUrlsRoot(repo).write(new File(www, PLUGIN_DOCUMENTATION_URLS_JSON_FILENAME), prettyPrint);
        }

        if (generatePluginVersions) {
            new PluginVersionsRoot("1", repo).writeWithSignature(new File(www, PLUGIN_VERSIONS_JSON_FILENAME), signer, prettyPrint);
        }

        if (generateReleaseHistory) {
            new ReleaseHistoryRoot(repo).write(new File(www, RELEASE_HISTORY_JSON_FILENAME), prettyPrint);
        }

        if (generateRecentReleases) {
            new RecentReleasesRoot(repo).write(new File(www, RECENT_RELEASES_JSON_FILENAME), prettyPrint);
        }

        if (generatePlatformPlugins) {
            new PlatformPluginsRoot(new File(Main.resourcesDir, PLATFORM_PLUGINS_RESOURCE_FILENAME)).writeWithSignature(new File(www, PLATFORM_PLUGINS_JSON_FILENAME), signer, prettyPrint);
        }

        directoryTreeBuilder.build(repo);
    }

    private String updateCenterPostCallJson(String updateCenterJson) {
        return "updateCenter.post(" + EOL + updateCenterJson + EOL + ");";
    }

    private String updateCenterPostMessageHtml(String updateCenterJson) {
        // needs the DOCTYPE to make JSON.stringify work on IE8
        return "\uFEFF<!DOCTYPE html><html><head><meta http-equiv='Content-Type' content='text/html;charset=UTF-8' /></head><body><script>window.onload = function () { window.parent.postMessage(JSON.stringify(" + EOL + updateCenterJson+ EOL + "),'*'); };</script></body></html>";
    }

    private static void writeToFile(String string, final File file) throws IOException {
        File parentFile = file.getParentFile();
        if (parentFile != null && !parentFile.isDirectory() && !parentFile.mkdirs()) {
            throw new IOException("Failed to create parent directory " + parentFile);
        }
        PrintWriter rhpw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
        rhpw.print(string);
        rhpw.close();
    }

    private void initializeLatestPluginVersions(boolean skip) throws IOException {
        if (skip) {
            LatestPluginVersions.initializeEmpty();
            return;
        }
        MavenRepository repo = DefaultMavenRepositoryBuilder.getInstance();
        if (allowedArtifactsListFile != null) {
            final Properties properties = new Properties();
            try (FileInputStream fis = new FileInputStream(allowedArtifactsListFile)) {
                properties.load(fis);
            }
            repo = new AllowedArtifactsListMavenRepository(properties).withBaseRepository(repo);
        }
        if (maxPlugins != null) {
            repo = new TruncatedMavenRepository(maxPlugins).withBaseRepository(repo);
        }
        if (onlyExperimental) {
            repo = new AlphaBetaOnlyRepository(false).withBaseRepository(repo);
        }
        if (!includeExperimental) {
            repo = new AlphaBetaOnlyRepository(true).withBaseRepository(repo);
        }
        LatestPluginVersions.initialize(repo);
    }

    private MavenRepository createRepository() throws Exception {

        MavenRepository repo = DefaultMavenRepositoryBuilder.getInstance();
        if (allowedArtifactsListFile != null) {
            final Properties properties = new Properties();
            try (FileInputStream fis = new FileInputStream(allowedArtifactsListFile)) {
                properties.load(fis);
            }
            repo = new AllowedArtifactsListMavenRepository(properties).withBaseRepository(repo);
        }
        if (maxPlugins != null) {
            repo = new TruncatedMavenRepository(maxPlugins).withBaseRepository(repo);
        }
        if (onlyExperimental) {
            repo = new AlphaBetaOnlyRepository(false).withBaseRepository(repo);
        }
        if (!includeExperimental) {
            repo = new AlphaBetaOnlyRepository(true).withBaseRepository(repo);
        }
        if (stableCore) {
            repo = new StableWarMavenRepository().withBaseRepository(repo);
        }
        if (capCore != null || capPlugin != null) {
            VersionNumber vp = capPlugin == null ? null : new VersionNumber(capPlugin);
            VersionNumber vc = capCore == null ? null : new VersionNumber(capCore);
            repo = new VersionCappedMavenRepository(vp, vc).withBaseRepository(repo);
        }
        if (javaVersion != null) {
            repo = new FilteringRepository().withPluginFilter(new JavaVersionPluginFilter(new JavaSpecificationVersion(this.javaVersion))).withBaseRepository(repo);
        }
        return repo;
    }

    private static final String WARNINGS_JSON_FILENAME = "warnings.json";
    private static final String UPDATE_CENTER_JSON_FILENAME = "update-center.json";
    private static final String UPDATE_CENTER_ACTUAL_JSON_FILENAME = "update-center.actual.json";
    private static final String UPDATE_CENTER_JSON_HTML_FILENAME = "update-center.json.html";
    private static final String PLUGIN_DOCUMENTATION_URLS_JSON_FILENAME = "plugin-documentation-urls.json";
    private static final String PLUGIN_VERSIONS_JSON_FILENAME = "plugin-versions.json";
    private static final String RELEASE_HISTORY_JSON_FILENAME = "release-history.json";
    private static final String RECENT_RELEASES_JSON_FILENAME = "recent-releases.json";
    private static final String PLATFORM_PLUGINS_JSON_FILENAME = "platform-plugins.json";
    private static final String PLATFORM_PLUGINS_RESOURCE_FILENAME = "platform-plugins.json";
    private static final String EOL = System.getProperty("line.separator");

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final Logger PACKAGE_LOGGER = Logger.getLogger(Main.class.getPackage().getName());
}

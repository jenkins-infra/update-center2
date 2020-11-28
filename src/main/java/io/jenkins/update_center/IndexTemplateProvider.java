package io.jenkins.update_center;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class IndexTemplateProvider {
    private static String globalTemplate;

    public IndexHtmlBuilder newIndexHtmlBuilder(File dir, String title) throws IOException {
        if (globalTemplate == null) {
            globalTemplate = initTemplate();
        }
        return new IndexHtmlBuilder(dir, title, globalTemplate);
    }

    protected String initTemplate() {
        Path template = Paths.get(Main.resourcesDir.getAbsolutePath(), "index-template.html");
        try {
            return new String(Files.readAllBytes(template), StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }
}

package io.jenkins.update_center;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JenkinsIndexTemplateProvider extends IndexTemplateProvider {
    private final String url;

    public JenkinsIndexTemplateProvider(String url) {
        super();
        this.url = url;
    }

    @Override
    protected String initTemplate() {
        String globalTemplate = "";
        Request request = new Request.Builder()
                .url(url).get().build();

        try {
            try (final ResponseBody body = new OkHttpClient().newCall(request).execute().body()) {
                Objects.requireNonNull(body); // guaranteed to be non-null by Javadoc
                globalTemplate = body.string();
            }
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, "Problem loading template", ioe);
        }
        Path style = Paths.get(Main.resourcesDir.getAbsolutePath(), "style.css");
        try {
            String styleContent = new String(Files.readAllBytes(style), StandardCharsets.UTF_8);
            // TODO instead of this replace, provide a better template on jenkins.io
            return globalTemplate.replace("{{ content }}",
                    "<style>" + styleContent + "</style>"
                            + "<div class=\"container\">"
                            + "<h1 class=\"mt-3\">{{ title }}</h1><div class=\"subtitle mb-2\">{{ subtitle }}</div>"
                            + "<ul class=\"artifact-list\">{{ content }}</ul>"
                            + "</div>");
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, "Problem loading template", ioe);
        }
        return globalTemplate;
    }

    private static final Logger LOGGER = Logger.getLogger(JenkinsIndexTemplateProvider.class.getName());
}

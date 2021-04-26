package io.jenkins.update_center;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

import java.io.IOException;
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
        return globalTemplate;
    }

    private static final Logger LOGGER = Logger.getLogger(JenkinsIndexTemplateProvider.class.getName());
}

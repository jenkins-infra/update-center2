package io.jenkins.update_center;

import com.alibaba.fastjson.JSON;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Plugin popularity is a unit-less integer value. A larger value means a plugin is more popular.
 * The data underlying this definition is undefined, whatever makes sense in context can be used.
 * <p>
 * This implementation just returns the number of installations at the moment, but that may change at any time.
 * <p>
 * The first iteration of this class returns decimal (float/double) values, but those caused problems for signature
 * validation in Jenkins due to the JSON normalization involved.
 */
public class Popularities {

    private static final String JSON_URL = "https://raw.githubusercontent.com/jenkins-infra/infra-statistics/gh-pages/plugin-installation-trend/latestNumbers.json";
    // or https://stats.jenkins.io/plugin-installation-trend/latestNumbers.json

    private static Popularities instance;

    private final Map<String, Integer> popularities;

    private Popularities(Map<String, Integer> popularities) {
        this.popularities = popularities;
    }

    private static void initialize() throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(JSON_URL))
                .build();
        try {
            final HttpClient client = HttpClient.newHttpClient();
            final HttpResponse<String> httpResp = client.send(request, HttpResponse.BodyHandlers.ofString());
            final JsonResponse response = JSON.parseObject(httpResp.body(), JsonResponse.class);

            if (response.plugins == null) {
                throw new IllegalArgumentException("Specified popularity URL '" + JSON_URL + "' does not contain a JSON object 'plugins'");
            }
            Map<String, Integer> popularities = response.plugins.keySet().stream()
                    .collect(Collectors.toMap(Function.identity(), value -> Integer.valueOf(response.plugins.get(value))));
            instance = new Popularities(popularities);
        } catch(InterruptedException e) {
            throw new IOException(e);
        }
    }

    private static class JsonResponse {
        public Map<String, String> plugins;
    }

    public static synchronized Popularities getInstance() throws IOException {
        if (instance == null) {
            initialize();
        }
        return instance;
    }

    public int getPopularity(String pluginId) {
        return this.popularities.getOrDefault(pluginId, 0);
    }
}

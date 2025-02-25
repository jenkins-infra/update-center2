package io.jenkins.update_center;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;

/**
 * Health score is an integer, from 0 to 100, which represents the health of the plugin.
 * The health is determined by the Plugin Health Scoring project hosted on https://github.com/jenkins-infra/plugin-health-scoring.
 * <p>
 * The list of plugins on which the scores are computed by this project is coming from the update-center file.
 * This means that when a plugin is for the first time in the update-center, it won't have any score.
 * </p>
 */
public class HealthScores {
    private static final Logger LOGGER = Logger.getLogger(HealthScores.class.getName());
    private static final String HEALTH_SCORES_URL = "https://reports.jenkins.io/plugin-health-scoring/scores.json";

    private static HealthScores instance;
    private final Map<String, Integer> healthScores;

    private HealthScores(Map<String, Integer> healthScores) {
        this.healthScores = healthScores;
    }

    public static synchronized HealthScores getInstance() {
        if (instance == null) {
            initialize();
        }
        return instance;
    }

    private static void initialize() {
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HEALTH_SCORES_URL))
                .GET()
                .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            final JsonResponse jsonResponse = JSON.parseObject(response.body(), JsonResponse.class);
            if (jsonResponse.plugins == null) {
                throw new IOException("Specified healthScore URL '" + HEALTH_SCORES_URL + "' does not contain a JSON object 'plugins'");
            }

            final Map<String, Integer> healthScores = jsonResponse.plugins.keySet().stream()
                    .collect(Collectors.toMap(Function.identity(), pluginId -> jsonResponse.plugins.get(pluginId).value));
            instance = new HealthScores(healthScores);
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.WARNING, e.getMessage());
            instance = new HealthScores(Map.of());
        }
    }

    private static class JsonResponse {
        public Map<String, PluginResponse> plugins;

    }
    private static class PluginResponse {
        public int value;
    }

    public Integer getHealthScore(String pluginId) {
        return this.healthScores.get(pluginId);
    }
}

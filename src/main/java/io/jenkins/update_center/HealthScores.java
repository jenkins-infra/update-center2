package io.jenkins.update_center;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import io.jenkins.update_center.util.HttpHelper;
import okhttp3.OkHttpClient;
import okhttp3.Request;

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
        final Request request = new Request.Builder().url(HEALTH_SCORES_URL).get().build();

        try {
            final String bodyString = HttpHelper.getResponseBody(new OkHttpClient(), request);
            final JsonResponse response = JSON.parseObject(bodyString, JsonResponse.class);
            if (response.plugins == null) {
                throw new IllegalArgumentException("Specified popularity URL '" + HEALTH_SCORES_URL + "' does not contain a JSON object 'plugins'");
            }

            final Map<String, Integer> healthScores = response.plugins.keySet().stream().collect(Collectors.toMap(Function.identity(), pluginId -> response.plugins.get(pluginId).value));
            instance = new HealthScores(healthScores);
        } catch (IOException e) {
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

    public int getHealthScore(String pluginId) {
        return this.healthScores.getOrDefault(pluginId, 0);
    }
}

package io.jenkins.update_center;

import com.alibaba.fastjson.JSON;
import io.jenkins.update_center.util.HttpHelper;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.IOException;
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
        Request request = new Request.Builder().url(JSON_URL).get().build();

        String bodyString = HttpHelper.getResponseBody(new OkHttpClient(), request);

        JsonResponse response = JSON.parseObject(bodyString, JsonResponse.class);
        if (response.plugins == null) {
            throw new IllegalArgumentException("Specified popularity URL '" + JSON_URL + "' does not contain a JSON object 'plugins'");
        }

        Map<String, Integer> popularities = response.plugins.keySet().stream().collect(Collectors.toMap(Function.identity(), value -> Integer.valueOf(response.plugins.get(value))));
        instance = new Popularities(popularities);
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

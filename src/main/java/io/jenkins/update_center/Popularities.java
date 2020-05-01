package io.jenkins.update_center;

import net.sf.json.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Plugin popularity is a unit-less decimal value. A larger value means a plugin is more popular.
 * The data underlying this definition is undefined, whatever makes sense in context can be used.
 * This implementation currently looks at install count, a plugin with no installs is '0.0', the most installed plugin is '1.0', but that may change at any time.
 */
public class Popularities {

    private static final String JSON_URL = "https://raw.githubusercontent.com/jenkins-infra/infra-statistics/gh-pages/plugin-installation-trend/latestNumbers.json";
    // or http://stats.jenkins.io/plugin-installation-trend/latestNumbers.json

    private static Popularities instance;

    private final Map<String, Integer> popularities;
    private final int maxPopularity;

    private Popularities(Map<String, Integer> popularities, int maxPopularity) {
        this.popularities = popularities;
        this.maxPopularity = maxPopularity;
    }

    private static void initialize() throws IOException {

        Map<String, Integer> popularities = new HashMap<>();
        int maxPopularity = 0;
        Request request = new Request.Builder().url(JSON_URL).get().build();
        final ResponseBody body = new OkHttpClient().newCall(request).execute().body();
        if (body == null) {
            throw new IOException("null body");
        }
        String bodyString = body.string();
        JSONObject jsonResponse = JSONObject.fromObject(bodyString);
        if (!jsonResponse.has("plugins")) {
            throw new IllegalArgumentException("Specified popularity URL '" + JSON_URL + "' does not contain a JSON object 'plugins'");
        }
        final JSONObject plugins = jsonResponse.getJSONObject("plugins");
        for (Iterator it = plugins.keys(); it.hasNext(); ) {
            String pluginId = it.next().toString();
            final int popularity = plugins.getInt(pluginId);
            if (popularity > maxPopularity) {
                maxPopularity = popularity;
            }
            popularities.put(pluginId, popularity);
        }
        instance = new Popularities(popularities, maxPopularity);
    }

    public static synchronized Popularities getInstance() throws IOException {
        if (instance == null) {
            initialize();
        }
        return instance;
    }

    public float getPopularity(String pluginId) {
        return this.popularities.getOrDefault(pluginId, 0).floatValue() / Integer.valueOf(maxPopularity).floatValue();
    }
}

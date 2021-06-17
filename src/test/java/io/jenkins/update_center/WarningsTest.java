package io.jenkins.update_center;

import io.jenkins.update_center.util.HttpHelper;
import junit.framework.Assert;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class WarningsTest {
    @Test
    public void testValidJsonFile() throws Exception {
        String warningsText = IOUtils.toString(new FileInputStream(new File("resources/warnings.json")));
        JSONArray warnings = JSONArray.fromObject(warningsText);

        for (int i = 0 ; i < warnings.size() ; i++) {
            JSONObject o = warnings.getJSONObject(i);
            assertNonEmptyString(o.getString("id"));
            assertNonEmptyString(o.getString("type"));
            assertNonEmptyString(o.getString("name"));
            assertNonEmptyString(o.getString("message"));
            assertNonEmptyString(o.getString("url"));
            JSONArray versions = o.getJSONArray("versions");
            for (int j = 0 ; j < versions.size() ; j++) {
                JSONObject version = versions.getJSONObject(j);
                String pattern = version.getString("pattern");
                assertNonEmptyString(pattern);
                Pattern p = Pattern.compile(pattern);
            }
        }
    }

    private void assertNonEmptyString(String str) {
        Assert.assertNotNull(str);
        Assert.assertFalse("".equals(str));
    }

    static class Warning {
        public String id;
        public Map<Pattern, Boolean> versions = new HashMap<>();
    }

    private Map<String, List<Warning>> loadPluginWarnings() throws IOException {
        Map<String, List<Warning>> loadedWarnings = new HashMap<>();

        String warningsText = IOUtils.toString(Files.newInputStream(new File(Main.resourcesDir, "warnings.json").toPath()));
        JSONArray warnings = JSONArray.fromObject(warningsText);

        for (int i = 0 ; i < warnings.size() ; i++) {
            JSONObject o = warnings.getJSONObject(i);

            if (o.getString("type").equals("core")) {
                continue;
            }

            Warning warning = new Warning();
            warning.id = o.getString("url") + " / " + o.getString("id");

            JSONArray versions = o.getJSONArray("versions");
            for (int j = 0 ; j < versions.size() ; j++) {
                JSONObject version = versions.getJSONObject(j);
                String pattern = version.getString("pattern");
                assertNonEmptyString(pattern);

                if (pattern.contains("beta")) {
                    // ignore for this test as these don't show in release history but we don't have an experimental release history
                    continue;
                }

                Pattern p = Pattern.compile(pattern);
                warning.versions.put(p, false);
            }

            if (!loadedWarnings.containsKey(o.getString("name"))) {
                loadedWarnings.put(o.getString("name"), new ArrayList<Warning>());
            }
            loadedWarnings.get(o.getString("name")).add(warning);
        }
        return loadedWarnings;
    }

    private static void testForWarning(String gav, Map<String, List<Warning>> warnings) {
        String[] gavParts = gav.split(":");
        String pluginId = gavParts[1];
        String version = gavParts[2];
        if (warnings.containsKey(pluginId)) {
            for (Warning warning : warnings.get(pluginId)) {
                Map<Pattern, Boolean> versions = warning.versions;
                for (Pattern p : versions.keySet()) {
                    if (p.matcher(version).matches()) {
                        versions.replace(p, true);
                        // written to target/surefire-reports/io.jenkins.update_center.WarningsTest-output.txt
                        System.out.println("Warning " + warning.id + " matches " + gav);
                    } else {
                        System.out.println("Warning " + warning.id + " does NOT match " + gav);
                    }
                }
            }
        }
    }

    @Test
    public void testWarningsAgainstReleaseHistory() throws IOException {

        Map<String, List<Warning>> warnings = loadPluginWarnings();

        OkHttpClient client = new OkHttpClient.Builder().build();
        Request request = new Request.Builder().url("https://updates.jenkins.io/release-history.json").get().build();

        String releaseHistoryText = HttpHelper.getResponseBody(client, request);
        JSONObject json = JSONObject.fromObject(releaseHistoryText);

        JSONArray dates = json.getJSONArray("releaseHistory");

        for (int dateIndex = 0; dateIndex < dates.size(); dateIndex++) {
            JSONObject date = dates.getJSONObject(dateIndex);

            JSONArray releases = date.getJSONArray("releases");
            for (int releaseIndex = 0; releaseIndex < releases.size(); releaseIndex++) {
                JSONObject release = releases.getJSONObject(releaseIndex);

                try {
                    String gav = release.getString("gav");
                    testForWarning(gav, warnings);
                } catch (JSONException ex) {
                    // TODO wtf?
                }
            }
        }

        // TODO figure out how to deal with blacklisted plugins
//        for (Map.Entry<String, Warning> warningEntry : warnings.entrySet()) {
//            Warning warning = warningEntry.getValue();
//            for (Map.Entry<Pattern, Boolean> patternBooleanEntry : warning.versions.entrySet()) {
//                if (!patternBooleanEntry.getValue()) {
//                    Assert.fail("Pattern " + patternBooleanEntry.getKey().toString() + " did not match any release");
//                }
//            }
//        }
    }
}

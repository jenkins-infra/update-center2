package org.jvnet.hudson.update_center;

import net.sf.json.JSONObject;
import okhttp3.Authenticator;
import okhttp3.Cache;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.OkUrlFactory;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jvnet.hudson.update_center.util.UrlToGitHubSlugConverter;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.OkHttp3Connector;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class GitHubSource {


    private static String GITHUB_API_USERNAME = System.getenv("GITHUB_USERNAME");
    private static String GITHUB_API_PASSWORD = System.getenv("GITHUB_PASSWORD");
    private static File GITHUB_API_CACHE = new File(System.getenv().getOrDefault("GITHUB_CACHEDIR", "githubCache"));

    /* Using the OkHttp Cache reduces request rate limit use, but isn't actually faster, so let's cache the repo list manually in this file */
    private static File GITHUB_REPO_LIST = new File(GITHUB_API_CACHE, "repo-list.txt");

    private Set<String> repoNames;
    private Map<String, List<String>> topicNames = null;
    private Map<String, Boolean> githubIssuesEnabled = null;


    private void init() {
        try {
            if (GITHUB_API_USERNAME != null && GITHUB_API_PASSWORD != null) {
                this.getRepositoryData("jenkinsci");
            }
        } catch (IOException e) {
            // ignore, fall back to dumb mode
        }
    }

    protected String getGraphqlUrl() {
        return "https://api.github.com/graphql";
    }

    private void getRepositoryData(String organization) throws IOException {
        if (this.topicNames != null && this.githubIssuesEnabled != null) {
            return;
        }
        this.topicNames = new HashMap<>();
        this.githubIssuesEnabled = new HashMap<>();
        this.repoNames = new TreeSet<>(new Comparator<String>() {
          @Override
          public int compare(String o1, String o2) {
            return o1.compareToIgnoreCase(o2);
          }
        });

        System.err.println("Retrieving GitHub topics...");
        Cache cache = new Cache(GITHUB_API_CACHE, 20L * 1024 * 1024); // 20 MB cache
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.cache(cache);
        if (GITHUB_API_USERNAME != null && GITHUB_API_PASSWORD != null)
        {
            builder.authenticator(new Authenticator() {
                @Nullable
                @Override
                public Request authenticate(Route route, Response response) throws IOException {
                    String credential = Credentials.basic(GITHUB_API_USERNAME, GITHUB_API_PASSWORD);
                    return response.request().newBuilder().header("Authorization", credential).build();
                }
            });
        }
        OkHttpClient client = builder.build();

        boolean hasNextPage = true;
        String endCursor = null;

        while (hasNextPage) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("query", String.format("{\n" +
                    "  organization(login: %s) {\n" +
                    "    repositories(first: 100, after: %s) {\n" +
                    "      pageInfo {\n" +
                    "        startCursor\n" +
                    "        hasNextPage\n" +
                    "        endCursor\n" +
                    "      }\n" +
                    "      edges {\n" +
                    "        node {\n" +
                    "          name\n" +
                    "          hasIssuesEnabled\n" +
                    "          repositoryTopics(first:100) {\n" +
                    "            edges {\n" +
                    "              node {\n" +
                    "                topic {\n" +
                    "                  name\n" +
                    "                }\n" +
                    "              }\n" +
                    "            }\n" +
                    "          }\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}\n",
                    "\"" + organization.replace("\"", "\\\"") + "\"",
                    endCursor == null ? "null" : "\"" + endCursor.replace("\"", "\\\"") + "\""
            ));
            System.err.println(String.format("Retrieving GitHub topics with end token... %s", endCursor));

            Request request = new Request.Builder()
                    .url(this.getGraphqlUrl())
                    .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonObject.toString()))
                    .build();

            String bodyString = client.newCall(request).execute().body().string();

            JSONObject jsonResponse = JSONObject.fromObject(bodyString);
            if (jsonResponse.has("errors")) {
                throw new IOException(
                        jsonResponse.getJSONArray("errors").toString()// .stream().map(o -> ((JSONObject)o).getString("message")).collect( Collectors.joining( "," ) )
                );
            }

            if (jsonResponse.has("message") && !jsonResponse.has("data")) {
                throw new IOException(jsonResponse.getString("message"));
            }

            JSONObject repositories = jsonResponse.getJSONObject("data").getJSONObject("organization").getJSONObject("repositories");

            hasNextPage = repositories.getJSONObject("pageInfo").getBoolean("hasNextPage");
            endCursor = repositories.getJSONObject("pageInfo").getString("endCursor");

            for (Object repository : repositories.getJSONArray("edges")) {
                JSONObject node = ((JSONObject) repository).getJSONObject("node");
                if (node.getJSONObject("repositoryTopics").getJSONArray("edges").size() == 0) {
                    continue;
                }
                String name = node.getString("name");
                this.repoNames.add(name);
                this.githubIssuesEnabled.put(name, node.getBoolean("hasIssuesEnabled"));
                this.topicNames.put(name, new ArrayList<>());
                for (Object repositoryTopic : node.getJSONObject("repositoryTopics").getJSONArray("edges")) {
                    this.topicNames.get(name).add(
                            ((JSONObject) repositoryTopic)
                                    .getJSONObject("node")
                                    .getJSONObject("topic")
                                    .getString("name")
                    );
                }
            }
        }
    }

    public List<String> getTopics(String organization, String repo) throws IOException {
        this.getRepositoryData(organization);
        if (!this.topicNames.containsKey(repo)) {
            return Collections.emptyList();
        }
        return this.topicNames.get(repo);
    }

    public boolean issuesEnabled(String organization, String repo) throws IOException {
        this.getRepositoryData(organization);
        return this.githubIssuesEnabled.get(repo);
    }


    private static GitHubSource instance;

    public static GitHubSource getInstance() {
        if (instance == null) {
            instance = new GitHubSource();
            instance.init();
        }
        return instance;
    }

    public boolean isRepoExisting(String url) {
        if (repoNames != null) {
            return repoNames.contains(url);
        } else {
            try {
                HttpClient client = new HttpClient();
                GetMethod get = new GetMethod(url);
                get.setFollowRedirects(true);
                if (client.executeMethod(get) >= 400) {
                    return false;
                }
            } catch (Exception e) {
                // that didn't work
                return false;
            }
            return true;
        }
    }
}

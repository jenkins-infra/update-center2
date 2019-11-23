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
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.OkHttp3Connector;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class GitHubSource {

    private GitHub github;

    private static String GITHUB_API_USERNAME = System.getenv("GITHUB_USERNAME");
    private static String GITHUB_API_PASSWORD = System.getenv("GITHUB_PASSWORD");
    private static File GITHUB_API_CACHE = new File(System.getenv().getOrDefault("GITHUB_CACHEDIR", "githubCache"));

    /* Using the OkHttp Cache reduces request rate limit use, but isn't actually faster, so let's cache the repo list manually in this file */
    private static File GITHUB_REPO_LIST = new File(GITHUB_API_CACHE, "repo-list.txt");

    private Set<String> repoNames;
    private Map<String, List<String>> topicNames = null;

    private GitHubSource() {
        try {
            if (GITHUB_API_USERNAME != null && GITHUB_API_PASSWORD != null) {

                this.repoNames = new TreeSet<>(new Comparator<String>() {
                    @Override
                    public int compare(String o1, String o2) {
                        return o1.compareToIgnoreCase(o2);
                    }
                });
                this.repoNames.addAll(getRepositoryNames());
            }
        } catch (IOException e) {
            // ignore, fall back to dumb mode
        }
    }

    private List<String> getRepositoryNames() throws IOException {
        // cache is valid for one hour
        if (!GITHUB_REPO_LIST.exists() || GITHUB_REPO_LIST.lastModified() < System.currentTimeMillis() - 1000 * 3600) {
            retrieveRepositoryNames();
        }
        return Files.readAllLines(GITHUB_REPO_LIST.toPath());
    }

    private void retrieveRepositoryNames() throws IOException {
        System.err.println("Retrieving GitHub repository names...");
        Cache cache = new Cache(GITHUB_API_CACHE, 20L * 1024 * 1024); // 20 MB cache
        github = new GitHubBuilder().withConnector(new OkHttp3Connector(new OkUrlFactory(new OkHttpClient.Builder().cache(cache).build()))).withPassword(GITHUB_API_USERNAME, GITHUB_API_PASSWORD).build();

        List<String> ret = new ArrayList<>();
        for (GHRepository repo : github.getOrganization("jenkinsci").listRepositories().withPageSize(100)) {
            ret.add(repo.getHtmlUrl().toString());
        }

        Files.write(GITHUB_REPO_LIST.toPath(), ret);
    }

    public Map<String, List<String>> getTopics(String organization) throws IOException {
        if (this.topicNames != null) {
            return this.topicNames;
        }
        System.err.println("Retrieving GitHub topics...");
        Cache cache = new Cache(GITHUB_API_CACHE, 20L * 1024 * 1024); // 20 MB cache
        OkHttpClient client = new OkHttpClient.Builder()
                .cache(cache)
                .authenticator(new Authenticator() {
                    @Nullable
                    @Override
                    public Request authenticate(Route route, Response response) throws IOException {
                        String credential = Credentials.basic(GITHUB_API_USERNAME, GITHUB_API_PASSWORD);
                        return response.request().newBuilder().header("Authorization", credential).build();
                    }
                }).build();

        boolean hasNextPage = true;
        String endCursor = null;

        Map<String,List<String>> ret = new HashMap<>();

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
                    .url("https://api.github.com/graphql")
                    .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonObject.toString()))
                    .build();

            JSONObject jsonResponse = JSONObject.fromObject(client.newCall(request).execute().body().string());
            if (jsonResponse.has("errors")) {
                throw new IOException(
                        jsonResponse.getJSONArray("errors").toString()// .stream().map(o -> ((JSONObject)o).getString("message")).collect( Collectors.joining( "," ) )
                );
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
                ret.put(name, new ArrayList<String>());
                for (Object repositoryTopic : node.getJSONObject("repositoryTopics").getJSONArray("edges")) {
                    ret.get(name).add(
                            ((JSONObject) repositoryTopic)
                                    .getJSONObject("node")
                                    .getJSONObject("topic")
                                    .getString("name")
                    );
                }
            }
        }
        this.topicNames = ret;
        return ret;
    }

    public List<String> getTopics(String organization, String repo) throws IOException {
        if (GITHUB_API_USERNAME != null && GITHUB_API_PASSWORD != null) {
            return this.getTopics(organization).get(repo);
        } else {
            System.err.println("Retrieving GitHub repository names...");
            Cache cache = new Cache(GITHUB_API_CACHE, 20L * 1024 * 1024); // 20 MB cache
            github = new GitHubBuilder().withConnector(new OkHttp3Connector(new OkUrlFactory(new OkHttpClient.Builder().cache(cache).build()))).withPassword(GITHUB_API_USERNAME, GITHUB_API_PASSWORD).build();

            return github.getRepository(organization + "/" + repo).listTopics();
        }
    }

    private static GitHubSource instance;

    public static GitHubSource getInstance() {
        if (instance == null) {
            instance = new GitHubSource();
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

package io.jenkins.update_center;

import io.jenkins.update_center.util.Environment;
import io.jenkins.update_center.util.HttpHelper;
import net.sf.json.JSONObject;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GitHubSource {
    private static final Logger LOGGER = Logger.getLogger(GitHubSource.class.getName());

    private static String GITHUB_API_USERNAME = Environment.getString("GITHUB_USERNAME");
    private static String GITHUB_API_PASSWORD = Environment.getString("GITHUB_PASSWORD");

    private Set<String> repoNames;
    private Map<String, List<String>> topicNames;
    private Map<String, String> defaultBranches;


    private void init() {
        try {
            if (GITHUB_API_USERNAME != null && GITHUB_API_PASSWORD != null) {
                this.initializeOrganizationData("jenkinsci");
            } else {
                throw new IllegalStateException("GITHUB_USERNAME and GITHUB_PASSWORD must be set");
            }
        } catch (IOException e) {
            // ignore, fall back to dumb mode
            LOGGER.log(Level.WARNING, "Failed to obtain data from GitHub", e);
        }
    }

    protected String getGraphqlUrl() {
        return "https://api.github.com/graphql";
    }

    protected void initializeOrganizationData(String organization) throws IOException {
        if (this.topicNames != null) {
            return; // Already initialized
        }
        this.topicNames = new HashMap<>();
        this.defaultBranches = new HashMap<>();
        this.repoNames = new TreeSet<>(String::compareToIgnoreCase);

        LOGGER.log(Level.INFO, "Retrieving GitHub repo data...");
        OkHttpClient client = new OkHttpClient.Builder().build();

        boolean hasNextPage = true;
        String endCursor = null;

        while (hasNextPage) {
            // TODO remove use of json-lib
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("query", String.format("{%n" +
                            "  organization(login: %s) {%n" +
                            "    repositories(first: 100, after: %s, orderBy:{field:NAME,direction:ASC}) {%n" +
                            "      pageInfo {%n" +
                            "        startCursor%n" +
                            "        hasNextPage%n" +
                            "        endCursor%n" +
                            "      }%n" +
                            "      edges {%n" +
                            "        node {%n" +
                            "          name%n" +
                            "          defaultBranchRef {%n" +
                            "            name%n" +
                            "          }%n" +
                            "          repositoryTopics(first:100) {%n" +
                            "            edges {%n" +
                            "              node {%n" +
                            "                topic {%n" +
                            "                  name%n" +
                            "                }%n" +
                            "              }%n" +
                            "            }%n" +
                            "          }%n" +
                            "        }%n" +
                            "      }%n" +
                            "    }%n" +
                            "  }%n" +
                            "}%n",
                    "\"" + organization.replace("\"", "\\\"") + "\"",
                    endCursor == null ? "null" : "\"" + endCursor.replace("\"", "\\\"") + "\""
            ));
            LOGGER.log(Level.FINE, String.format("Retrieving GitHub topics with end token... %s", endCursor));

            Request.Builder builder = new Request.Builder()
                    .url(this.getGraphqlUrl())
                    .post(RequestBody.create(jsonObject.toString(), MediaType.parse("application/json; charset=utf-8")));
            if (GITHUB_API_PASSWORD != null && GITHUB_API_USERNAME != null) {
                builder = builder.header("Authorization", Credentials.basic(GITHUB_API_USERNAME, GITHUB_API_PASSWORD));
            }
            Request request = builder.build();

            String bodyString = HttpHelper.getResponseBody(client, request);

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
                String name = node.getString("name");
                this.repoNames.add("https://github.com/" + organization + "/" + name);

                if (node.optJSONObject("defaultBranchRef") == null) {
                    // empty repo, so ignore everything else
                    LOGGER.log(Level.WARNING, "Unexpected empty GitHub repository: " + name);
                    continue;
                }
                final String defaultBranchName = node.getJSONObject("defaultBranchRef").getString("name");
                if (defaultBranchName != null) {
                    this.defaultBranches.put(organization + "/" + name, defaultBranchName);
                }

                if (node.getJSONObject("repositoryTopics").getJSONArray("edges").size() == 0) {
                    continue;
                }
                this.topicNames.put(organization + "/" + name, new ArrayList<>());
                for (Object repositoryTopic : node.getJSONObject("repositoryTopics").getJSONArray("edges")) {
                    this.topicNames.get(organization + "/" + name).add(
                            ((JSONObject) repositoryTopic)
                                    .getJSONObject("node")
                                    .getJSONObject("topic")
                                    .getString("name")
                    );
                }
            }
        }
        LOGGER.log(Level.INFO, "Retrieved GitHub repo data");
    }

    public List<String> getRepositoryTopics(String org, String repo) throws IOException { // TODO get rid of throws
        return this.topicNames == null ? Collections.emptyList() : this.topicNames.getOrDefault(org + "/" + repo, Collections.emptyList());
    }

    @CheckForNull
    public String getDefaultBranch(String org, String repo) {
        return this.defaultBranches.get(org + "/" + repo);
    }

    private static GitHubSource instance;

    public static synchronized GitHubSource getInstance() {
        if (instance == null) {
            GitHubSource gh = new GitHubSource();
            gh.init();
            instance = gh;
        }
        return instance;
    }


    public boolean isRepoExisting(String url) {
        return repoNames.contains(url);
    }
}

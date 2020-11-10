package io.jenkins.update_center;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.jenkins.update_center.util.Environment;
import net.sf.json.JSONObject;
import okhttp3.Cache;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GitHubSource {
    private static final Logger LOGGER = Logger.getLogger(GitHubSource.class.getName());

    private static String GITHUB_API_USERNAME = Environment.getString("GITHUB_USERNAME");
    private static String GITHUB_API_PASSWORD = Environment.getString("GITHUB_PASSWORD");
    private static File GITHUB_API_CACHE = new File(Environment.getString("GITHUB_CACHEDIR", "caches/github"));

    public static class RepoInformation {
        public boolean hasGithubIssuesEnabled;
        public String defaultBranch;
        public List<String> topicNames;
    }

    public static class GitHubRepo {
        public final String organization;
        public final String repoName;

        public GitHubRepo(@NonNull String organization, @NonNull String repoName) {
            this.organization = organization;
            this.repoName = repoName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GitHubRepo that = (GitHubRepo) o;
            return organization.equals(that.organization) &&
                    repoName.equals(that.repoName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(organization, repoName);
        }
    }

    private Set<String> repoNames;
    private Map<GitHubRepo, RepoInformation> repoMetadata;

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
        if (this.repoMetadata != null) {
            return;
        }
        this.repoMetadata = new HashMap<>();
        this.repoNames = new TreeSet<>(String::compareToIgnoreCase);

        LOGGER.log(Level.INFO, "Retrieving GitHub repo data...");
        Cache cache = new Cache(GITHUB_API_CACHE, 20L * 1024 * 1024); // 20 MB cache
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.cache(cache);
        if (GITHUB_API_USERNAME != null && GITHUB_API_PASSWORD != null) {
            builder.authenticator((route, response) -> {
                String credential = Credentials.basic(GITHUB_API_USERNAME, GITHUB_API_PASSWORD);
                return response.request().newBuilder().header("Authorization", credential).build();
            });
        }
        OkHttpClient client = builder.build();

        boolean hasNextPage = true;
        String endCursor = null;

        while (hasNextPage) {
            // TODO remove use of json-lib
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
                            "          defaultBranchRef {\n" +
                            "            name\n" +
                            "          }\n" +
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
            LOGGER.log(Level.FINE, String.format("Retrieving GitHub topics with end token... %s", endCursor));

            Request request = new Request.Builder()
                    .url(this.getGraphqlUrl())
                    .post(RequestBody.create(jsonObject.toString(), MediaType.parse("application/json; charset=utf-8")))
                    .build();

            String bodyString;
            try (final ResponseBody body = client.newCall(request).execute().body()) {
                Objects.requireNonNull(body); // guaranteed to be non-null by Javadoc
                bodyString = body.string();
            }

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
                if ( node.optJSONObject("defaultBranchRef") == null ) {
                    // empty repo, so ignore everything else
                    continue;
                }

                String name = node.getString("name");
                this.repoNames.add("https://github.com/" + organization + "/" + name);

                RepoInformation repoInformation = new RepoInformation();
                repoInformation.defaultBranch = node.getJSONObject("defaultBranchRef").getString("name");
                repoInformation.hasGithubIssuesEnabled = node.getBoolean("hasIssuesEnabled");

                repoMetadata.put(new GitHubRepo(organization, name), repoInformation);

                if (node.getJSONObject("repositoryTopics").getJSONArray("edges").size() == 0) {
                    continue;
                }
                repoInformation.topicNames = new ArrayList<>();
                for (Object repositoryTopic : node.getJSONObject("repositoryTopics").getJSONArray("edges")) {
                    repoInformation.topicNames.add(
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

    public List<String> getRepositoryTopics(GitHubRepo repo) {
        if (this.repoMetadata == null) {
            return Collections.emptyList();
        }
        RepoInformation repoInformation = this.repoMetadata.get(repo);
        if (repoInformation == null) {
            return Collections.emptyList();
        }
        if (repoInformation.topicNames == null) {
            return Collections.emptyList();
        }
        return repoInformation.topicNames;
    }

    public Boolean hasGithubIssuesEnabled(GitHubRepo repo) {
        if (this.repoMetadata == null) {
            return false;
        }
        RepoInformation repoInformation = this.repoMetadata.get(repo);
        if (repoInformation == null) {
            // TODO log warning
            return false;
        }
        return repoInformation.hasGithubIssuesEnabled;
    }

    public String getDefaultBranch(GitHubRepo repo) {
        if (this.repoMetadata == null) {
            return null;
        }
        RepoInformation repoInformation = this.repoMetadata.get(repo);
        if (repoInformation == null) {
            // TODO log warning
            return null;
        }
        return repoInformation.defaultBranch;
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
        return repoNames.contains(url);
    }

    public GitHubRepo getGitHubRepoFromURL(String scm) {
        if (!scm.contains("https://github.com/")) {
            return null;
        }

        String[] parts = scm.replaceFirst("https://github.com/", "").split("/");
        if (parts.length < 2) {
            return null;
        }
        return new GitHubRepo(parts[0], parts[1]);
    }

}

package org.jvnet.hudson.update_center;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.OkUrlFactory;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.OkHttp3Connector;
import org.kohsuke.github.extras.OkHttpConnector;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

public class GitHubSource {

    private GitHub github;

    private static String GITHUB_API_USERNAME = System.getenv("GITHUB_USERNAME");
    private static String GITHUB_API_PASSWORD = System.getenv("GITHUB_PASSWORD");
    private static File GITHUB_API_CACHE = new File(System.getenv().getOrDefault("GITHUB_CACHEDIR", "githubCache"));

    private Set<String> repoNames;

    private GitHubSource() {
        try {
            if (GITHUB_API_USERNAME != null && GITHUB_API_PASSWORD != null) {

                Cache cache = new Cache(GITHUB_API_CACHE, 20L*1024*1024); // 20 MB cache
                github = new GitHubBuilder().withConnector(new OkHttp3Connector(new OkUrlFactory(new OkHttpClient.Builder().cache(cache).build()))).withPassword(GITHUB_API_USERNAME, GITHUB_API_PASSWORD).build();

                this.repoNames = new TreeSet<>(new Comparator<String>() {
                    @Override
                    public int compare(String o1, String o2) {
                        return o1.compareToIgnoreCase(o2);
                    }
                });
                for (GHRepository repo : github.getOrganization("jenkinsci").getRepositories().values()) {
                    repoNames.add(StringUtils.stripEnd(repo.getHttpTransportUrl(), ".git"));
                }
            }
        } catch (IOException e) {
            // ignore, fall back to dumb mode
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

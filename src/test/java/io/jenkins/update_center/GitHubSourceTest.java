package io.jenkins.update_center;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class GitHubSourceTest {

    @Test
    public void getTopics() throws Exception {
        MockWebServer server = new MockWebServer();

        server.enqueue(new MockResponse().setBody(
                IOUtils.toString(
                        this.getClass().getClassLoader().getResourceAsStream("github_graphql_null.txt"),
                        "UTF-8"
                )
        ));
        server.enqueue(new MockResponse().setBody(
                IOUtils.toString(
                        this.getClass().getClassLoader().getResourceAsStream("github_graphql_Y3Vyc29yOnYyOpHOA0oRaA==.txt"),
                        "UTF-8"
                )
        ));
        // Start the server.
        server.start();

        GitHubSource gh = new GitHubSource() {
            @Override
            protected String getGraphqlUrl() {
                return server.url("/graphql").toString();
            }

            @Override
            public List<String> getRepositoryTopics(String org, String repo) throws IOException {
                return initializeOrganizationData(org).getOrDefault(org + "/" + repo, Collections.emptyList());
            }
        };
        assertEquals(
                Arrays.asList("pipeline"),
                gh.getRepositoryTopics("jenkinsci", "workflow-cps-plugin")
        );
        // Shut down the server. Instances cannot be reused.
        server.shutdown();
    }
}
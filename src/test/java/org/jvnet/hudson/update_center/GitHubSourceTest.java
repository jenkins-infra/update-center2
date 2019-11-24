package org.jvnet.hudson.update_center;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.util.Arrays;

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
        };
        assertEquals(
            gh.getTopics("jenkinsci", "workflow-cps-plugin"),
            Arrays.asList("pipeline")
        );
        // Shut down the server. Instances cannot be reused.
        server.shutdown();
    }
}
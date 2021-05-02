package io.jenkins.update_center;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.*;

public class GitHubSourceTest {

    @Test
    public void testCodeQL() throws Exception {
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

        GitHubSource gh = new MockWebServerGitHubSource(server);
        assertEquals(Arrays.asList("cmake","jenkins-plugin", "jenkins-builder", "pipeline"), gh.getRepositoryTopics("jenkinsci", "cmakebuilder-plugin"));
        assertEquals("incoming", gh.getDefaultBranch("jenkinsci", "jmdns"));
        // Shut down the server. Instances cannot be reused.
        server.shutdown();
    }

    private static class MockWebServerGitHubSource extends GitHubSource {
        private final MockWebServer server;

        private MockWebServerGitHubSource(MockWebServer server) throws IOException {
            this.server = server;
            initializeOrganizationData("jenkinsci");
        }

        @Override
        protected String getGraphqlUrl() {
            return server.url("/graphql").toString();
        }
    }
}
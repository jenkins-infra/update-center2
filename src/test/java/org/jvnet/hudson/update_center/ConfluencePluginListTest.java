package org.jvnet.hudson.update_center;

import hudson.plugins.jira.soap.ConfluenceSoapService;
import hudson.plugins.jira.soap.RemotePage;
import hudson.plugins.jira.soap.RemotePageSummary;
import junit.framework.TestCase;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConfluencePluginListTest extends TestCase {

    private ConfluenceSoapService confluence;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        confluence = mock(ConfluenceSoapService.class);
        when(confluence.getPage("", "JENKINS", "Plugins")).thenReturn(new RemotePage());
    }

    public void testUnknownUrlsAreIgnored() throws Exception {
        // Given a list of plugin pages that exist on the wiki
        addPluginPages(
                "https://wiki.jenkins-ci.org/display/JENKINS/Foo+Plugin",
                "https://wiki.jenkins-ci.org/display/JENKINS/Bar+Plugin"
        );

        // When we retrieve plugin info from Confluence
        ConfluencePluginList pluginList = new ConfluencePluginList(confluence);

        // Then we should not match URLs that don't exist on the wiki
        assertNull(pluginList.resolveWikiUrl(""));
        assertNull(pluginList.resolveWikiUrl(null));
        assertNull(pluginList.resolveWikiUrl("https://github.com/jenkinsci/jenkins"));
        assertNull(pluginList.resolveWikiUrl("https://wiki.jenkins-ci.org/display/JENKINS/Missing+Plugin"));

        // And we should not match URLs with the wrong prefix
        assertNull(pluginList.resolveWikiUrl("https://wiki.example.com/display/JENKINS/Foo+Plugin"));
    }

    public void testResolveCanonicalUrl() throws Exception {
        // Given a list of plugin pages that exist on the wiki
        addPluginPages(
                "https://wiki.jenkins-ci.org/display/JENKINS/Foo+Plugin",
                "https://wiki.jenkins-ci.org/display/JENKINS/Bar+Plugin"
        );

        // When we retrieve plugin info from Confluence
        ConfluencePluginList pluginList = new ConfluencePluginList(confluence);

        // Then we should get the canonical URL for items that can be found on the wiki
        final String expected = "https://wiki.jenkins-ci.org/display/JENKINS/Foo+Plugin";
        assertEquals(expected, pluginList.resolveWikiUrl("https://wiki.jenkins-ci.org/display/JENKINS/Foo+Plugin"));
        assertEquals(expected, pluginList.resolveWikiUrl("http://wiki.jenkins-ci.org/display/JENKINS/Foo+Plugin"));
        assertEquals(expected, pluginList.resolveWikiUrl("http://wiki.hudson-ci.org/display/JENKINS/Foo+Plugin"));
        assertEquals(expected, pluginList.resolveWikiUrl("http://hudson.gotdns.com/wiki/display/JENKINS/Foo+Plugin"));
    }

    public void testUrlsWithEncoding() throws Exception {
        // Given some URLs that have URL-encoded characters
        addPluginPages(
                "https://wiki.jenkins-ci.org/display/JENKINS/Jenkins+Speaks%21+Plugin",
                "https://wiki.jenkins-ci.org/display/JENKINS/Plugin+Usage+Plugin+%28Community%29"
        );

        // When we retrieve plugin info from Confluence
        ConfluencePluginList pluginList = new ConfluencePluginList(confluence);

        // Then we should match POM URLs which may be differently encoded
        assertNotNull(pluginList.resolveWikiUrl("http://wiki.jenkins-ci.org/display/JENKINS/Jenkins+Speaks!+Plugin"));
        assertNotNull(pluginList.resolveWikiUrl("http://wiki.jenkins-ci.org/display/JENKINS/Plugin+Usage+Plugin+(Community)"));
    }

    public void testUrlWithPageId() throws Exception {
        // Given a wiki URL with a page ID, as Confluence doesn't like certain characters in wiki URLs
        addPluginPages("https://wiki.jenkins-ci.org/pages/viewpage.action?pageId=60915753");

        // When we retrieve plugin info from the wiki
        ConfluencePluginList pluginList = new ConfluencePluginList(confluence);

        // Then the page should be found, even although it doesn't fit into the usual /display/JENKINS/<name> format
        assertNotNull(pluginList.resolveWikiUrl("https://wiki.jenkins-ci.org/pages/viewpage.action?pageId=60915753"));
    }

    public void testMalformedWikiUrls() throws Exception {
        // Given a list of plugin pages that exist on the wiki
        addPluginPages("https://wiki.jenkins-ci.org/display/JENKINS/Foo+Plugin");

        // When we retrieve plugin info from Confluence
        ConfluencePluginList pluginList = new ConfluencePluginList(confluence);

        // Then we should reject bizarre URLs in the POM, but not crash
        assertNull(pluginList.resolveWikiUrl("file:///etc/passwd"));
        assertNull(pluginList.resolveWikiUrl("wiki.jenkins-ci.org/display/JENKINS/Foo+Plugin"));
        assertNull(pluginList.resolveWikiUrl("https://wiki.jenkins-ci.org/display/JENKINS/Spaces in URL Plugin"));
    }

    /** Adds zero or more plugins to the "wiki". */
    private void addPluginPages(String... urls) throws Exception {
        RemotePageSummary[] pluginPages = new RemotePageSummary[urls.length];
        for (int i = 0, n = urls.length; i < n; i++) {
            pluginPages[i] = new RemotePageSummary(0, 0, "", "", urls[i], 0); // we only care about the page URL
        }
        when(confluence.getChildren(any(String.class), any(Long.class))).thenReturn(pluginPages);
    }

}
package org.jvnet.hudson.update_center.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class UrlToGitHubSlugConverterTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void convert_simple_url_passes() {
        String given = "https://github.com/jenkinsci/blueocean-plugin";

        String then = UrlToGitHubSlugConverter.convert(given).toString();

        assertThat(then, is("jenkinsci/blueocean-plugin"));
    }

    @Test
    public void convert_url_with_slash_suffix_passes() {
        String given = "https://github.com/jenkinsci/blueocean-plugin/";

        String then = UrlToGitHubSlugConverter.convert(given).toString();

        assertThat(then, is("jenkinsci/blueocean-plugin"));
    }

    @Test
    public void convert_with_git_suffix_passes() {
        String given = "https://github.com/jenkinsci/blueocean-plugin.git";

        String then = UrlToGitHubSlugConverter.convert(given).toString();

        assertThat(then, is("jenkinsci/blueocean-plugin"));
    }

    @Test
    public void convert_with_non_jenkinsci_org_passes() {
        String given = "https://github.com/jenkins-infra/some-repo.git";

        String then = UrlToGitHubSlugConverter.convert(given).toString();

        assertThat(then, is("jenkins-infra/some-repo"));
    }

    @Test
    public void convert_with_non_github_url_throws_expected_exception() {
        String given = "https://issues.jenkins-ci.org";

        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Invalid url: " + given);

        String then = UrlToGitHubSlugConverter.convert(given).toString();
    }

    @Test
    public void convert_with_null_throws_expected_exception() {
        String given = "";

        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("URL must be present");

        String then = UrlToGitHubSlugConverter.convert(given).toString();
    }

    @Test
    public void convert_with_empty_throws_expected_exception() {
        String given = null;

        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("URL must be present");

        String then = UrlToGitHubSlugConverter.convert(given).toString();
    }
}
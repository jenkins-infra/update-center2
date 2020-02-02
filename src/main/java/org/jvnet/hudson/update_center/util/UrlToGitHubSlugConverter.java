package org.jvnet.hudson.update_center.util;

import javax.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;

public final class UrlToGitHubSlugConverter {

    private UrlToGitHubSlugConverter() {
    }

    /**
     * Converts a github url to its 'slug'
     * @param url a url like: https://github.com/jenkinsci/blueocean-plugin
     * @return the github slug, jenkinsci/blueocean-plugin
     */
    public static String convert(@Nonnull String url) {
        if (StringUtils.isBlank(url)) {
            throw new IllegalArgumentException("URL must be present");
        }

        if (!url.contains("github.com")) {
            throw new IllegalArgumentException("Invalid url: " + url);
        }

        String slug = StringUtils.remove(url, "https://github.com/");
        String slugWithNoGit = StringUtils.remove(slug, ".git");

        return StringUtils.stripEnd(slugWithNoGit, "/");
    }
}

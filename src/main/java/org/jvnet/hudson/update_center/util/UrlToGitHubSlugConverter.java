package org.jvnet.hudson.update_center.util;

import javax.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;

public final class UrlToGitHubSlugConverter {

    public static final class SlugFields {
        public final String organization;
        public final String name;

        public SlugFields(String organization, String name) {
          this.organization = organization;
          this.name = name;
        }

        @Override
        public String toString() {
            return this.organization + "/" + this.name;
        }
    }

    private UrlToGitHubSlugConverter() {
    }

    /**
     * Converts a github url to its 'slug'
     * @param url a url like: https://github.com/jenkinsci/blueocean-plugin
     * @return the github slug, jenkinsci/blueocean-plugin
     */
    public static SlugFields convert(@Nonnull String url) {
        if (StringUtils.isBlank(url)) {
            throw new IllegalArgumentException("URL must be present");
        }

        if (!url.contains("github.com")) {
            throw new IllegalArgumentException("Invalid url: " + url);
        }

        String slug = StringUtils.remove(url, "https://github.com/");
        String slugWithNoGit = StringUtils.remove(slug, ".git");

        String[] parts = StringUtils.stripEnd(slugWithNoGit, "/").split("/");
        return new SlugFields(parts[0], parts[1]);
    }
}

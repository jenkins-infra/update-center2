package io.jenkins.update_center;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JenkinsIndexTemplateProvider extends IndexTemplateProvider {
    private final String url;

    public JenkinsIndexTemplateProvider(String url) {
        super();
        this.url = url;
    }

    @Override
    protected String initTemplate() {
        String globalTemplate = "";

        try {
            Document doc = Jsoup.connect(url).get();

            doc.getElementsByAttribute("href").forEach(element -> setAbsoluteUrl(element, "href"));
            doc.getElementsByAttribute("src").forEach(element -> setAbsoluteUrl(element, "src"));
            globalTemplate = doc.toString();
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, "Problem loading template", ioe);
        }
        return globalTemplate;
    }

    private void setAbsoluteUrl(Element element, String attributeName) {
        final String attribute = element.attr(attributeName);
        if (attribute.startsWith("/")) {
            element.attr(attributeName, "https://www.jenkins.io" + attribute);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(JenkinsIndexTemplateProvider.class.getName());
}

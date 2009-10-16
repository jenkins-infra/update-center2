import org.jvnet.hudson.confluence.Confluence;

import java.net.URL;
import java.util.HashMap;

import hudson.plugins.jira.soap.RemotePage;
import hudson.plugins.jira.soap.ConfluenceSoapService;

/**
 * @author Kohsuke Kawaguchi
 */
public class ConfluenceTestDrive {
    public static void main(String[] args) throws Exception {
        ConfluenceSoapService service = Confluence.connect(new URL("http://wiki.hudson-ci.org/"));
        RemotePage page = service.getPage("", "HUDSON", "Ivy Plugin");
        HashMap m = new HashMap();
        m.put("style","clean");
        // this renders HTML but because of relative links it can only render correctly from the same Wiki.
        System.out.println(service.renderContent("","HUDSON",page.getId(),"testing [format|http://www.google.com/] formatting *yes*",m));
//        System.out.println(page.getContent());
    }
}

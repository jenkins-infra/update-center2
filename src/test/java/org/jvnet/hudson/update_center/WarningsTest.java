package org.jvnet.hudson.update_center;

import junit.framework.Assert;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;

import java.util.regex.Pattern;

public class WarningsTest {
    public void testValidJsonFile() throws Exception {
        String warningsText = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("warnings.json"));
        JSONArray warnings = JSONArray.fromObject(warningsText);

        for (int i = 0 ; i < warnings.size() ; i++) {
            JSONObject o = warnings.getJSONObject(i);
            assertNonEmptyString(o.getString("id"));
            assertNonEmptyString(o.getString("type"));
            assertNonEmptyString(o.getString("name"));
            assertNonEmptyString(o.getString("message"));
            assertNonEmptyString(o.getString("url"));
            JSONArray versions = o.getJSONArray("versions");
            for (int j = 0 ; j < versions.size() ; j++) {
                JSONObject version = versions.getJSONObject(j);
                String pattern = version.getString("pattern");
                assertNonEmptyString(pattern);
                Pattern p = Pattern.compile(pattern);
            }
        }
    }

    private void assertNonEmptyString(String str) {
        Assert.assertNotNull(str);
        Assert.assertFalse("".equals(str));
    }
}

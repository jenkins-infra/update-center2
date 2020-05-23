package io.jenkins.update_center;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.serializer.SerializerFeature;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class JsonChecksumTest {
    @Test
    public void testJsonChecksum() throws Exception {

        // step 1: Generate JSON using fastjson
        Root root = new Root();

        StringWriter fastJsonWriter = new StringWriter();
        JSON.writeJSONString(fastJsonWriter, root, SerializerFeature.DisableCircularReferenceDetect);
        String fastJsonOutput = fastJsonWriter.getBuffer().toString();

        // step 2: Load generated JSON with json-lib, re-write it canonically
        JSONObject o = JSONObject.fromObject(fastJsonOutput);

        StringWriter jsonLibWriter = new StringWriter();
        o.writeCanonical(jsonLibWriter);
        String jsonLibOutput = jsonLibWriter.getBuffer().toString();

        Assert.assertEquals("JSONlib and fastjson should generate same output", jsonLibOutput, fastJsonOutput);

        // step 3: Generate equivalent JSON with json-lib, compare
        StringWriter jsonLibWriter2 = new StringWriter();
        root.toJSON().writeCanonical(jsonLibWriter2);
        String jsonLibOutput2 = jsonLibWriter2.getBuffer().toString();

        Assert.assertEquals("JSONlib after load and standalone should be the same", jsonLibOutput, jsonLibOutput2);
    }

    private static class Root {
        @JSONField
        public String baz;

        @JSONField
        public List<ListEntry> entries;

        @JSONField
        public String foo;

        @JSONField
        public String bar;

        @JSONField
        public long getLong() {
            return Long.MAX_VALUE;
        };

        @JSONField
        public float random;

        public Root() {
            random = (float) Math.random();
            entries = new ArrayList<>();
            entries.add(new ListEntry("one"));
            entries.add(new ListEntry("two"));
            entries.add(new ListEntry("three"));
            entries.add(new ListEntry("four"));

            bar = "bar";
            baz = "qux";
            foo = "Quuux";
        }

        public JSONObject toJSON() {
            JSONObject o = new JSONObject();
            o.put("bar", bar);
            o.put("foo", foo);
            o.put("baz", baz);
            o.put("random", random);
            o.put("long", getLong());
            JSONArray a = new JSONArray();
            entries.forEach(e -> a.add(e.toJSON()));
            o.put("entries", a);
            return o;
        }
    }

    private static class ListEntry {
        @JSONField
        public String value;
        public ListEntry(String value) {
            this.value = value;
        }

        @JSONField
        public String getFoo() {
            return "\u0000";
        }
        @JSONField
        public String getBar() {
            return "\uD834\uDD1E";
        }

        public JSONObject toJSON() {
            JSONObject o = new JSONObject();
            o.put("value", value);
            o.put("foo", getFoo());
            o.put("bar", getBar());
            return o;
        }
    }
}

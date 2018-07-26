/**
 * The MIT License
 *
 * Copyright (c) 2011, Jerome Lacoste
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
import org.jvnet.hudson.update_center.*;

import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class HPIResolver {

    public void resolveHPI(String groupIdArtifactIdVersion) throws Exception {
        String[] split = groupIdArtifactIdVersion.split(":");
        String groupId = split[0];
        String artifactId1 = split[1];
        String version = split[2];
        resolveHPI(groupId, artifactId1, version);
    }

    public void resolveHPI(String groupId, String artifactId, String version) throws Exception {
        System.out.format("Resolving HPI %s:%s:%s\n", groupId, artifactId, version);
        MavenRepositoryImpl mavenRepository = DefaultMavenRepositoryBuilder.getInstance();

        HPI plugin = mavenRepository.findPlugin(groupId, artifactId, version);

        MavenArtifact mavenArtifact = new MavenArtifact(mavenRepository, plugin.artifact);
        Manifest manifest = mavenArtifact.getManifest();

        Attributes mainAttributes = manifest.getMainAttributes();

        List<Attributes.Name> attributeNames = sortAttributes(mainAttributes);
        for (Object entry : attributeNames) {
            System.out.println(entry + ":" + mainAttributes.get(entry));
        }
    }

    private static List<Attributes.Name> sortAttributes(Attributes mainAttributes) {
        List<Attributes.Name> attributeNames = toList(mainAttributes.keySet());
        Collections.sort(attributeNames, new AttributesNameNaturalOrder());
        return attributeNames;
    }

    private static List<Attributes.Name> toList(Set<Object> objects) {
        List<Attributes.Name> result = new ArrayList<Attributes.Name>();
        for (Object object : objects) {
            result.add((Attributes.Name) object);
        }
        return result;
    }

    private static class AttributesNameNaturalOrder implements Comparator<Attributes.Name> {
        public int compare(Attributes.Name o1, Attributes.Name o2) {
            return o1.toString().compareTo(o2.toString());
        }

    }

    public static void main(String[] args) throws Exception {
        // args = new String[] { "org.jenkins-ci.plugins:ivy:1.17" };

        if (args.length == 0) {
            usage();
            System.exit(-1);
        }
        String artifactId = args[0];
        new HPIResolver().resolveHPI(artifactId);
    }

    private static void usage() {
        System.out.println("HPIResolver groupId:artifactId:version");
        System.out.println("\t resolves a particular plugin and prints its manifest information");
    }
}

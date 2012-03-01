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
package org.jvnet.hudson.update_center;

import org.sonatype.nexus.index.context.UnsupportedExistingLuceneIndexException;

import java.io.IOException;
import java.net.URL;

public class DefaultMavenRepositoryBuilder {
    private MavenRepositoryImpl instance = null;

    public DefaultMavenRepositoryBuilder() throws Exception {
        instance = new MavenRepositoryImpl();
    }

    public DefaultMavenRepositoryBuilder withRemoteRepositories() throws IOException, UnsupportedExistingLuceneIndexException {
        instance.addRemoteRepository("java.net2",
                new URL("http://updates.jenkins-ci.org/.index/nexus-maven-repository-index.gz"),
                new URL("http://repo.jenkins-ci.org/public/"));
        return this;
    }

    private DefaultMavenRepositoryBuilder withMaxPlugins(Integer maxPlugins) {
        instance.setMaxPlugins(maxPlugins);
        return this;
    }

    public MavenRepository getInstance() {
        return instance;
    }

    /**
     * @param maxPlugins can be null.
     * @return
     * @throws Exception
     */
    public static MavenRepositoryImpl createStandardInstance(Integer maxPlugins) throws Exception {
        return new DefaultMavenRepositoryBuilder().withRemoteRepositories().withMaxPlugins(maxPlugins).instance;
    }

    public static MavenRepositoryImpl createStandardInstance() throws Exception {
        return createStandardInstance(null);
    }
}

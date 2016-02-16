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

import java.net.URL;

public class DefaultMavenRepositoryBuilder {
    public static MavenRepositoryImpl createStandardInstance() throws Exception {
        return createStandardInstance(null, null, null, false, false);
    }

    public static MavenRepositoryImpl createStandardInstance(String repositoryName, String repository, String remoteIndex, boolean directLink,
            boolean includeSnapshots) throws Exception {
        MavenRepositoryImpl instance = new MavenRepositoryImpl(directLink, includeSnapshots);

        addRemoteRepository(instance, repositoryName, repository, remoteIndex, directLink);

        return instance;
    }

    public static MavenRepositoryImpl addRemoteRepository(MavenRepositoryImpl instance, String repositoryName, String repository, String remoteIndex,
            boolean directLink) throws Exception {
        if (repositoryName == null)
        {
            repositoryName = "public";
        }

        if (repository == null)
        {
            repository = "http://repo.jenkins-ci.org/public/";
        }

        URL repositoryUrl = new URL(repository);
        if (remoteIndex != null)
        {
            instance.addRemoteRepository(repositoryName, new URL(repositoryUrl, remoteIndex), repositoryUrl);
        }
        else
        {
            instance.addRemoteRepository(repositoryName, repositoryUrl);
        }
        return instance;
    }
}

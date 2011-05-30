/*
 * The MIT License
 *
 * Copyright (c) 2011, Nigel Magnay / NiRiMa
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
package jenkins.repository.updateCenter;

import com.nirima.jenkins.repo.AbstractRepositoryDirectory;
import com.nirima.jenkins.repo.RepositoryDirectory;
import com.nirima.jenkins.repo.RepositoryElement;

import java.util.Arrays;
import java.util.Collection;

public class UpdateCenterRepositoryRoot extends AbstractRepositoryDirectory implements RepositoryDirectory {

    UpdateCenterRepository updateCenterRepository;

    public UpdateCenterRepositoryRoot(UpdateCenterRepository updateCenterRepository, RepositoryDirectory parent) {
        super(parent);
        this.updateCenterRepository = updateCenterRepository;
    }

    @Override
    public Collection<? extends RepositoryElement> getChildren() {
        return Arrays.asList( new RepositoryElement [] {new UpdateCenterFile(this) } );  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getName() {
        return "updates";  //To change body of implemented methods use File | Settings | File Templates.
    }
}

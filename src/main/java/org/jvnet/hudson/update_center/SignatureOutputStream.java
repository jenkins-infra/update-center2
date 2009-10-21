package org.jvnet.hudson.update_center;

import org.apache.commons.io.output.NullOutputStream;

import java.security.Signature;
import java.security.SignatureException;
import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class SignatureOutputStream extends FilterOutputStream {
    private final Signature sig;

    public SignatureOutputStream(OutputStream out, Signature sig) {
        super(out);
        this.sig = sig;
    }

    public SignatureOutputStream(Signature sig) {
        this(new NullOutputStream(),sig);
    }

    @Override
    public void write(int b) throws IOException {
        try {
            sig.update((byte)b);
            out.write(b);
        } catch (SignatureException e) {
            throw (IOException)new IOException(e.getMessage()).initCause(e);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        try {
            sig.update(b,off,len);
            out.write(b,off,len);
        } catch (SignatureException e) {
            throw (IOException)new IOException(e.getMessage()).initCause(e);
        }
    }
}

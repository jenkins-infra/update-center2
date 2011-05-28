package org.jvnet.hudson.update_center;

import org.bouncycastle.ocsp.OCSPReqGenerator;

import java.io.*;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Simple collection of utility stuff for Files.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public final class FileUtils {

    /**
     * Unzips a zip/jar archive into the specified directory.
     *
     * @param file        the file to unzip
     * @param toDirectory the directory to extract the files to.
     */
    public static void unzip(File file, File toDirectory) throws IOException {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(file);

            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                if (entry.isDirectory()) {
                    File dir = new File(toDirectory, entry.getName());
                    dir.mkdirs();
                    continue;
                }
                File entryFile = new File(toDirectory, entry.getName());
                entryFile.getParentFile().mkdirs();
                copyInputStream(zipFile.getInputStream(entry),
                        new BufferedOutputStream(new FileOutputStream(entryFile)));
            }
        } finally {
            if (zipFile != null) {
                zipFile.close();
            }
        }
    }

    private static void copyInputStream(InputStream in, OutputStream out)
            throws IOException {
        byte[] buffer = new byte[1024];
        int len;

        while ((len = in.read(buffer)) >= 0)
            out.write(buffer, 0, len);

        in.close();
        out.close();
    }

    public static void copyInto(File file, File toDir, String filename) throws IOException {
        File nFile = new File(toDir, filename);
        org.apache.commons.io.FileUtils.copyFile(file, nFile);
    }

    public static Iterable<File> getFileIterator(File dir, String extension) {
        Iterator i = org.apache.commons.io.FileUtils.iterateFiles(dir, new String[]{extension}, true);
        LinkedList<File> l = new LinkedList<File>();
        while(i.hasNext()) {
            l.add((File) i.next());
        }
        return l;
    }
}

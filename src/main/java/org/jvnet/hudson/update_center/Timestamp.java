/*
 * The MIT License
 *
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

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class Timestamp {

    private final long time;

    public Timestamp(long time) {
        this.time = time;
    }

    long getTimestamp() throws IOException {
        return time;
    }

    public String getTimestampAsString() throws IOException {
        long lastModified = getTimestamp();
        SimpleDateFormat bdf = getDateFormat();

        return bdf.format(lastModified);
    }

    public Date getTimestampAsDate() throws IOException {
        long lastModified = getTimestamp();
        SimpleDateFormat bdf = getDateFormat();

        Date tsDate;

        try {
            tsDate = bdf.parse(bdf.format(new Date(lastModified)));
        } catch (ParseException pe) {
            throw new IOException(pe.getMessage());
        }

        return tsDate;
    }

    @Override
    public String toString() {
        try {
            return getTimestampAsString();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return "??";
    }

    public static SimpleDateFormat getDateFormat() {
        return new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    }
}

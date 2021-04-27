package io.jenkins.update_center;

import io.jenkins.update_center.json.WithSignature;
import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class TimestampTest {
    @Test
    public void checkTimestamp() throws Exception {
        WithSignature w = new WithSignature() {
        };

        String timestamp = w.getGenerationTimestamp();

        Assert.assertTrue("format as expected", timestamp.matches("^202[0-9][-][0-9]{2}[-][0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}.[0-9]{3}Z$"));

        Instant parsed = Instant.parse(timestamp);

        Assert.assertTrue("before now", parsed.isBefore(Instant.now()));
        Assert.assertTrue("very recent", parsed.isAfter(Instant.now().minus(1, ChronoUnit.SECONDS)));
    }
}

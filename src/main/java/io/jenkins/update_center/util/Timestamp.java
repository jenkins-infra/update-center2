package io.jenkins.update_center.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class Timestamp {
    public static final String TIMESTAMP = DateTimeFormatter.ISO_DATE_TIME.format(Instant.now().atOffset(ZoneOffset.UTC).withNano(0));
}

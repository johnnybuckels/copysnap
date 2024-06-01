package com.github.johannesbuchholz.copysnap.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class TimeUtils {

    private static final ZoneId ZONE_ID = ZoneId.systemDefault();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss[Z]")
            .withZone(ZONE_ID);

    public static ZonedDateTime epochStart() {
        return ZonedDateTime.ofInstant(Instant.EPOCH, ZONE_ID);
    }

    public static String nowAsString() {
        return ZonedDateTime.now().format(FORMATTER);
    }
    public static String asString(ZonedDateTime time) {
        return FORMATTER.format(time);
    }

    public static ZonedDateTime fromString(String timeString) {
        return ZonedDateTime.from(FORMATTER.parse(timeString));
    }

    private TimeUtils() {
        // do not instantiate
    }

}

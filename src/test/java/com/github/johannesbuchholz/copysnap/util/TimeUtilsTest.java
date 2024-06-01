package com.github.johannesbuchholz.copysnap.util;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeUtilsTest {

    @Test
    void timeParseTest() {
        String now = TimeUtils.nowAsString();
        System.out.println(now);

        ZonedDateTime zonedDateTime = TimeUtils.fromString(now);
        System.out.println(zonedDateTime);

        String asString = TimeUtils.asString(zonedDateTime);
        System.out.println(asString);

        assertEquals(now, asString);
    }

}
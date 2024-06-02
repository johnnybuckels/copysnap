package com.github.johannesbuchholz.copysnap.model;

import com.github.johannesbuchholz.copysnap.model.state.CheckpointChecksum;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CheckpointChecksumTest {

    @Test
    void testStringContents_success() {
        // given
        String s = """
                This is a String with some paragraphs. Actually, this
                is much easier than expected.
                
                Hopefully, this all pays of at some point. I am really grateful.
                """;
        // when
        CheckpointChecksum checkpointChecksum = CheckpointChecksum.from(new ByteArrayInputStream(s.getBytes()));

        // then
        assertTrue(checkpointChecksum.hasSameChecksum(new ByteArrayInputStream(s.getBytes())));
    }

    @Test
    void testStringContents_failure_paragraph() {
        // given
        String s = """
                This is a String with some paragraphs. Actually, this
                is much easier than expected.
                
                Hopefully, this all pays of at some point. I am really grateful.
                """;
        // when
        CheckpointChecksum checkpointChecksum = CheckpointChecksum.from(new ByteArrayInputStream(s.getBytes()));

        // then
        assertFalse(checkpointChecksum.hasSameChecksum(new ByteArrayInputStream(s.substring(1).getBytes())));
    }

    @Test
    void testStringContents_failure_short() {
        // given
        String s1 = "a";
        String s2 = "z";
        // when
        CheckpointChecksum checkpointChecksum = CheckpointChecksum.from(new ByteArrayInputStream(s1.getBytes()));

        // then
        assertFalse(checkpointChecksum.hasSameChecksum(new ByteArrayInputStream(s2.getBytes())));
    }

    @Test
    void testLargeContents_success() {
        Random rng = new Random();
        // given
        byte[] bytes = new byte[1_000_000];
        rng.nextBytes(bytes);

        // when
        CheckpointChecksum checkpointChecksum = CheckpointChecksum.from(new ByteArrayInputStream(bytes));

        // then
        assertTrue(checkpointChecksum.hasSameChecksum(new ByteArrayInputStream(bytes)));
    }

    @Test
    void testLargeContents_failure() {
        // given
        byte[] bytes1 = {0};
        byte[] bytes2 = {1};

        // when
        CheckpointChecksum checkpointChecksum = CheckpointChecksum.from(new ByteArrayInputStream(bytes1));

        // then
        assertFalse(checkpointChecksum.hasSameChecksum(new ByteArrayInputStream(bytes2)));
    }

    @ParameterizedTest
    @Disabled("Only for fine tuning")
    @ValueSource(ints = {
            256,
            10_000,
            100_000,
            1_000_000,  // 100 KB
            10_000_000,  // 10 MB
            100_000_000  // 100 MB
    })
    void speedTest_failure(int arrayLength) {
        // prepare intervals
        int[] intervals = new int[10];
        for(int pointer = 0; pointer < intervals.length; pointer++)
            intervals[pointer] = pointer * arrayLength / intervals.length;

        List<Map<Integer, Duration>> durations = new ArrayList<>();
        // when changing bytes at the intervals and take durations of check
        int retries = 10;
        for (int count = 0; count < retries; count++) {
            byte[] bytes = new byte[arrayLength];
            HashMap<Integer, Duration> durationByMark = new HashMap<>();
            for (int intervalMark : intervals) {
                // setup new byte array
                new Random().nextBytes(bytes);
                CheckpointChecksum checkpointChecksum = CheckpointChecksum.from(new ByteArrayInputStream(bytes));

                // change one byte
                bytes[intervalMark] = (byte) (bytes[intervalMark] > 0 ? bytes[intervalMark] - 1 : bytes[intervalMark] + 1);

                // measure difference check
                Instant start = Instant.now();
                assert !checkpointChecksum.hasSameChecksum(new ByteArrayInputStream(bytes)) : "Has not changed...";
                Duration d = Duration.between(start, Instant.now());

                durationByMark.put(intervalMark, d);
            }
            durations.add(durationByMark);
        }

        // print results
        System.out.println("\n# Results for array length (%s retries): %s".formatted(retries, arrayLength));
        Map<Integer, List<Map.Entry<Integer, Duration>>> durationsByMark = durations.stream()
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.groupingBy(Map.Entry::getKey));

        durationsByMark.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue().stream().map(Map.Entry::getValue).mapToLong(Duration::toNanos).average().orElseThrow()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> System.out.println("%s (%d)".formatted(entry.getValue() / 1_000_000, entry.getKey())));
    }

    @Test
    void testLargeContents_failure_unequal_array_length_random_contents() {
        Random rng = new Random();
        // given
        byte[] bytesOld = new byte[1_000_000];
        byte[] bytesNew = new byte[500_000];
        rng.nextBytes(bytesOld);
        rng.nextBytes(bytesNew);

        // when
        CheckpointChecksum checkpointChecksumOld = CheckpointChecksum.from(new ByteArrayInputStream(bytesOld));

        // then
        assertFalse(checkpointChecksumOld.hasSameChecksum(new ByteArrayInputStream(bytesNew)));
    }

    @Test
    void testLargeContents_failure_unequal_array_length_single_changed_byte() {
        Random rng = new Random();
        // given
        byte[] bytesOld = new byte[1_000_000];
        byte[] bytesNew = new byte[500_000];

        // when
        bytesNew[rng.nextInt(bytesNew.length)] = 1;
        CheckpointChecksum checkpointChecksumOld = CheckpointChecksum.from(new ByteArrayInputStream(bytesOld));

        // then
        assertFalse(checkpointChecksumOld.hasSameChecksum(new ByteArrayInputStream(bytesNew)));
    }

    @Test
    void testTransferring() {
        Random rng = new Random();
        // given
        byte[] bytes = new byte[1_000];
        rng.nextBytes(bytes);

        ByteArrayInputStream is = new ByteArrayInputStream(bytes);
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        // when
        CheckpointChecksum ignore = CheckpointChecksum.byTransferring(is, os);

        // then
        assertArrayEquals(bytes, os.toByteArray());
    }

}
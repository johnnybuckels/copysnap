package com.github.johannesbuchholz.copysnap.model;

import com.github.johannesbuchholz.copysnap.model.state.CheckpointChecksum;
import com.github.johannesbuchholz.copysnap.model.state.FileState;
import com.github.johannesbuchholz.copysnap.model.state.FileSystemState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileSystemStateTest {

    private static final int HASH_SIZE = 16;
    private static final Random RNG = new Random();

    private static Path tmpFilePath;

    @BeforeAll
    public static void createTmpDir() throws IOException {
        tmpFilePath = Files.createTempDirectory("copysnap_unittest");
        System.out.println("CREATED TEMP DIRECTORY AT " + tmpFilePath);
    }

    @Test
    @Disabled("Only for manual performance test")
    public void serde() throws IOException {
        int fileCount = 10_000;

        // given
        long start = System.currentTimeMillis();
        FileSystemState.Builder builder = FileSystemState.builder();
        IntStream.range(0, fileCount).forEach(i -> builder.add(generateRandomFileState()));
        FileSystemState fst = builder.build();
        long initEnd = System.currentTimeMillis();

        // when ser + de
        Path tempFile = Files.createTempFile(tmpFilePath, "tmpfile" + System.currentTimeMillis(), ".tmp");
        fst.write(Files.newOutputStream(tempFile));
        long serEnd = System.currentTimeMillis();
        FileSystemState deserializedFst = FileSystemState.read(Files.newInputStream(tempFile));
        long deEnd = System.currentTimeMillis();

        // then
        System.out.println("File count: " + fileCount);
        System.out.println("Init: " + (initEnd - start) / 1000.0);
        System.out.println("Ser: " + (serEnd - initEnd) / 1000.0);
        System.out.println("De: " + (deEnd - serEnd) / 1000.0);
        assertEquals(fst.paths(), deserializedFst.paths());
    }

    private FileState generateRandomFileState() {
        byte[] bytes = new byte[HASH_SIZE];
        RNG.nextBytes(bytes);
        // 97 = 'a', 122 = 'z'
        Path p = getRandomRelativePath();
        CheckpointChecksum checkpointChecksum = CheckpointChecksum.from(new ByteArrayInputStream(bytes));
        return new FileState(p, Instant.now(), checkpointChecksum);
    }

    /**
     * @return Path.of("g/a/c/a/a/h/i/").
     */
    private Path getRandomRelativePath() {
        String[] randomParts = Stream.concat(
                        RNG.ints(97, 123)
                                .limit(15)
                                .mapToObj(Character::toChars)
                                .map(String::new),
                        Stream.of(UUID.randomUUID().toString()))
                .toArray(String[]::new);
        return Path.of("", randomParts);
    }

}

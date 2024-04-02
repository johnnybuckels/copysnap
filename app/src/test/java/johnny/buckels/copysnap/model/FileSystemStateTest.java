package johnny.buckels.copysnap.model;

import org.junit.jupiter.api.BeforeAll;
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
    private static final int MAX_PATH_LENGTH = 8;
    private static final Random RNG = new Random();

    // TODO: Change to actual tmp folder when testing of tests is done
    private static final Path TMP_FILE_PATH = Path.of(System.getProperty("user.dir")).resolve("tmp");

    @BeforeAll
    public static void createTmpDir() throws IOException {
        System.out.println("CREATE TEMP DIRECTORY AT " + TMP_FILE_PATH);
        Files.createDirectories(TMP_FILE_PATH);
    }

    @Test
    public void serde() throws IOException {
        int fileCount = 10_000;

        // given
        long start = System.currentTimeMillis();
        Path root = Path.of("/root/path");
        FileSystemState.Builder builder = FileSystemState.builder(root);
        IntStream.range(0, fileCount).forEach(i -> builder.add(generateRandomFileState(15)));
        FileSystemState fst = builder.build();
        long initEnd = System.currentTimeMillis();

        // when ser + de
        Path tempFile = Files.createTempFile(TMP_FILE_PATH, "tmpfile" + System.currentTimeMillis(), ".tmp");
        fst.write(tempFile);
        long serEnd = System.currentTimeMillis();
        FileSystemState deserializedFst = FileSystemState.read(tempFile);
        long deEnd = System.currentTimeMillis();

        // then

        System.out.println("File count: " + fileCount);
        System.out.println("Init: " + (initEnd - start) / 1000.0);
        System.out.println("Ser: " + (serEnd - initEnd) / 1000.0);
        System.out.println("De: " + (deEnd - serEnd) / 1000.0);
        assertEquals(fst.getRootPath(), deserializedFst.getRootPath());
        assertEquals(fst.getStates(), deserializedFst.getStates());
    }

    /**
     * File state with root "/".
     */
    private FileState generateRandomFileState() {
        return generateRandomFileState(RNG.nextInt(MAX_PATH_LENGTH));
    }

    private FileState generateRandomFileState(int randomPartLength) {
        byte[] bytes = new byte[HASH_SIZE];
        RNG.nextBytes(bytes);
        // 97 = 'a', 122 = 'z'
        Path p = getRandomRelativePath(randomPartLength);
        CheckpointChecksum checkpointChecksum = CheckpointChecksum.from(new ByteArrayInputStream(bytes));
        return new FileState(p, Instant.now(), checkpointChecksum);
    }

    /**
     * @return Path.of("g/a/c/a/a/h/i/").
     */
    private Path getRandomRelativePath(int length) {
        String[] randomParts = Stream.concat(
                        RNG.ints(97, 123)
                                .limit(length)
                                .mapToObj(Character::toChars)
                                .map(String::new),
                        Stream.of(UUID.randomUUID().toString()))
                .toArray(String[]::new);
        return Path.of("", randomParts);
    }

}

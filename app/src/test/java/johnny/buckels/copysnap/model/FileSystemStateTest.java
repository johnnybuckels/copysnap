package johnny.buckels.copysnap.model;

import johnny.buckels.copysnap.testutils.FileStateGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileSystemStateTest {

    // TODO: Change to actual tmp folder when testing of tests is done
    private static final Path TMP_FILE_PATH = Path.of(System.getProperty("user.dir")).resolve("tmp");

    private final FileStateGenerator fileStateGenerator = new FileStateGenerator();

    @BeforeAll
    public static void createTmpDir() throws IOException {
        Files.createDirectories(TMP_FILE_PATH);
    }

    @Test
    public void test_SerDe() throws IOException {
        int fileCount = 100;

        // given
        long start = System.currentTimeMillis();
        Path root = Path.of("/root/path");
        FileSystemState.Builder builder = FileSystemState.builder(root);
        IntStream.range(0, fileCount).forEach(i -> builder.add(fileStateGenerator.generateFileState(root)));
        FileSystemState fst = builder.build();
        long initEnd = System.currentTimeMillis();

        // when ser + de
        Path tempFile = Files.createTempFile(TMP_FILE_PATH, "tmpfile" + System.currentTimeMillis(), ".tmp");
        fst.writeTo(tempFile);
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

}

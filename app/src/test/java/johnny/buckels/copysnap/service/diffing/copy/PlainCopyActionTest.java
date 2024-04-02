package johnny.buckels.copysnap.service.diffing.copy;

import johnny.buckels.copysnap.model.CheckpointChecksum;
import johnny.buckels.copysnap.model.FileState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PlainCopyActionTest {

    // TODO: Change to actual tmp folder when testing of tests is done
    private static final Path TMP_FILE_PATH = Path.of(System.getProperty("user.dir")).resolve("tmp");
    private static final Random RNG = new Random();

    @BeforeAll
    public static void createTmpDir() throws IOException {
        System.out.println("CREATE TEMP DIRECTORY AT " + TMP_FILE_PATH);
        Files.createDirectories(TMP_FILE_PATH);
    }

    @Test
    void testPlainCopy() throws IOException {
        // write random bytes
        Path from = getTmpPath();
        byte[] bytes = new byte[1024];
        RNG.nextBytes(bytes);
        Files.write(from, bytes);

        Path to = TMP_FILE_PATH.resolve("toDir/" + from.getFileName().toString());

        PlainCopyAction pca = new PlainCopyAction(TMP_FILE_PATH, to.getParent(), to.getFileName());
        Optional<FileState> fileStateOpt = pca.perform();

        assertTrue(fileStateOpt.isPresent());

        FileState fileState = fileStateOpt.get();
        assertEquals(CheckpointChecksum.from(new ByteArrayInputStream(bytes)), fileState.getChecksum());
        assertArrayEquals(Files.readAllBytes(from), Files.readAllBytes(to));
    }

    private static Path getTmpPath() throws IOException {
        return Files.createTempFile(TMP_FILE_PATH, "tmpfile_" + PlainCopyActionTest.class.getSimpleName() + "_" + System.currentTimeMillis(), ".tmp");
    }

}
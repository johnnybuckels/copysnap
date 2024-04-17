package johnny.buckels.copysnap.service.diffing.copy;

import johnny.buckels.copysnap.model.CheckpointChecksum;
import johnny.buckels.copysnap.model.FileState;
import johnny.buckels.copysnap.service.diffing.FileSystemAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PlainCopyActionTest {

    private static class DoNothingFsa implements FileSystemAccessor {

        @Override
        public Instant getLastModifiedTime(Path p) {
            return Instant.now();
        }

        @Override
        public boolean areChecksumsEqual(CheckpointChecksum expectedChecksum, Path p) {
            return false;
        }

        @Override
        public OutputStream createNewOutputStream(Path path) {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream createNewInputStream(Path path) {
            return InputStream.nullInputStream();
        }

        @Override
        public void createDirectories(Path path) {
            // do nothing
        }

        @Override
        public Stream<Path> findFiles(Path path) {
            return Stream.empty();
        }

        @Override
        public void createSymbolicLink(Path absDestination, Path absSource) {
            // do nothing
        }
    }

    // TODO: Change to actual tmp folder when testing of tests is done
    private static final Path TMP_FILE_PATH = Path.of(System.getProperty("user.dir")).resolve("tmp");
    private static final Random RNG = new Random();

    @BeforeAll
    public static void createTmpDir() throws IOException {
        System.out.println("CREATE TEMP DIRECTORY AT " + TMP_FILE_PATH);
        Files.createDirectories(TMP_FILE_PATH);
    }

    @Test
    @Disabled("Enable as soon as Mock is ready")
    void testPlainCopy() throws IOException {
        // write random bytes
        Path from = getTmpPath();
        byte[] bytes = new byte[1024];
        RNG.nextBytes(bytes);
        Files.write(from, bytes);

        Path to = TMP_FILE_PATH.resolve("toDir/" + from.getFileName().toString());

        PlainCopyAction pca = new PlainCopyAction(TMP_FILE_PATH, to.getParent(), to.getFileName());
        // TODO: Implement proper fsa Mock
        Optional<FileState> fileStateOpt = pca.perform(new DoNothingFsa());

        assertTrue(fileStateOpt.isPresent());

        FileState fileState = fileStateOpt.get();
        assertEquals(CheckpointChecksum.from(new ByteArrayInputStream(bytes)), fileState.getChecksum());
        assertArrayEquals(Files.readAllBytes(from), Files.readAllBytes(to));
    }

    private static Path getTmpPath() throws IOException {
        return Files.createTempFile(TMP_FILE_PATH, "tmpfile_" + PlainCopyActionTest.class.getSimpleName() + "_" + System.currentTimeMillis(), ".tmp");
    }

}
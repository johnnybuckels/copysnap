package com.github.johannesbuchholz.copysnap.service.diffing.copy;

import com.github.johannesbuchholz.copysnap.model.state.CheckpointChecksum;
import com.github.johannesbuchholz.copysnap.model.state.FileState;
import com.github.johannesbuchholz.copysnap.service.diffing.testutils.TestFileSystemAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
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
        // given
        Path sourceRoot = Path.of("/source/root");

        Path relPath = Path.of("some/where/to/file.txt");
        Instant lastModifiedInstant = Instant.now();
        byte[] bytes = new byte[1024];
        RNG.nextBytes(bytes);

        Path destinationRoot = Path.of("/destination/root");
        ByteArrayOutputStream toFileSink = new ByteArrayOutputStream();

        PlainCopyAction pca = new PlainCopyAction(sourceRoot, destinationRoot, relPath);

        TestFileSystemAccessor fsaMock = TestFileSystemAccessor.builder()
                .setDataSinksByPath(Map.of(destinationRoot.resolve(relPath), toFileSink))
                .setContentByPath(Map.of(sourceRoot.resolve(relPath), bytes))
                .setLastModified(Map.of(sourceRoot.resolve(relPath), lastModifiedInstant))
                .build();

        // when
        Optional<FileState> fileStateOpt = pca.perform(fsaMock);

        // then
        assertTrue(fileStateOpt.isPresent());
        FileState fileState = fileStateOpt.get();
        assertEquals(lastModifiedInstant, fileState.lastModified());
        assertEquals(CheckpointChecksum.from(new ByteArrayInputStream(bytes)), fileState.getChecksum());
        assertArrayEquals(bytes, toFileSink.toByteArray());
    }

}
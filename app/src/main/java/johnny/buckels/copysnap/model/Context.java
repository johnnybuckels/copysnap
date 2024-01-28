package johnny.buckels.copysnap.model;

import johnny.buckels.copysnap.service.SnapshotService;
import johnny.buckels.copysnap.service.hashing.ParallelHashingService;
import johnny.buckels.copysnap.service.logging.AbstractMessageProducer;
import johnny.buckels.copysnap.service.logging.Message;
import johnny.buckels.copysnap.service.logging.MessageConsumer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class Context extends AbstractMessageProducer {

    static final String CONTEXT_PROPERTIES_FILE_NAME = "context.properties";
    private static final String LATEST_FILE_STATE_FILE_NAME = ".latest";

    private ContextProperties properties;

    Context(ContextProperties properties) {
        this.properties = properties;
    }

    public void createSnapshot() {
        FileSystemState newState = computeFileSystemState(properties.getSourceDir());
        Path newSnapshotDir = properties.getSnapshotsHomeDir().resolve(generateSnapshotName());

        SnapshotService snapshotService = new SnapshotService(newState, loadLatestFileSystemState());
        snapshotService.setMessageConsumer(messageConsumer);
        snapshotService.createNewSnapshot(newSnapshotDir);
        writeLatestFileSystemState(newState.switchRootTo(newSnapshotDir));
        properties = properties.getNewUpdated(newSnapshotDir);
    }

    /**
     * Intended to reproduce a file system state of an older snapshot or to repair a broken file system state.
     * @param sourceDir The directory to compute the new state from.
     */
    public void recomputeFileSystemState(Path sourceDir) {
        FileSystemState fileSystemState = computeFileSystemState(sourceDir);
        properties = properties.getNewUpdated(fileSystemState.getRootPath());
        writeLatestFileSystemState(fileSystemState);
    }

    public Context withMessageConsumer(MessageConsumer messageConsumer) {
        setMessageConsumer(Objects.requireNonNull(messageConsumer));
        return this;
    }

    /**
     * @return The path to the saved context properties file.
     * @throws UncheckedIOException if writing fails.
     */
    public Path writeProperties() {
        Path snapshotsHomeDir = properties.getSnapshotsHomeDir();
        Path destination = snapshotsHomeDir.resolve(CONTEXT_PROPERTIES_FILE_NAME);
        try {
            Files.createDirectories(snapshotsHomeDir);
            properties.write(destination);
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not save context properties for context at %s: %s", snapshotsHomeDir, e.getMessage()), e);
        }
        return destination;
    }

    public String toDisplayString() {
        return properties.toDisplayString();
    }

    // TODO: Remove and replace with mechanism only hashing files that are about to be copied.
    private FileSystemState computeFileSystemState(Path sourceDir) {
        ParallelHashingService parallelHashingService = new ParallelHashingService();
        parallelHashingService.setMessageConsumer(messageConsumer);
        return parallelHashingService.computeState(sourceDir);
    }

    private String generateSnapshotName() {
        return ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
    }

    private FileSystemState loadLatestFileSystemState() {
        Path latestSnapshotFile = properties.getSnapshotsHomeDir().resolve(LATEST_FILE_STATE_FILE_NAME);
        if (Files.isRegularFile(latestSnapshotFile)) {
            FileSystemState fileSystemState = FileSystemState.read(latestSnapshotFile);
            messageConsumer.consumeMessage(Message.info("Successfully loaded snapshot "
                    + fileSystemState.getCreated().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
            return fileSystemState;
        } else {
            messageConsumer.consumeMessage(Message.info("No latest file system state found."));
            return FileSystemState.empty();
        }
    }

    private void writeLatestFileSystemState(FileSystemState fileSystemState) {
        fileSystemState.writeTo(properties.getSnapshotsHomeDir().resolve(LATEST_FILE_STATE_FILE_NAME));
    }

}

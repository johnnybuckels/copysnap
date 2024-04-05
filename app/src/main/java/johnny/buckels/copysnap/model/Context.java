package johnny.buckels.copysnap.model;

import johnny.buckels.copysnap.service.SnapshotService;
import johnny.buckels.copysnap.service.logging.AbstractMessageProducer;
import johnny.buckels.copysnap.service.logging.Message;
import johnny.buckels.copysnap.service.logging.MessageConsumer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.stream.Stream;

public class Context extends AbstractMessageProducer {

    static final String CONTEXT_PROPERTIES_FILE_NAME = "context.properties";
    private static final String LATEST_FILE_STATE_FILE_NAME = ".latest";

    private ContextProperties properties;

    Context(ContextProperties properties) {
        this.properties = properties;
    }

    public void createSnapshot() {
        Path newSnapshotDir = properties.getSnapshotsHomeDir().resolve(generateSnapshotName());

        SnapshotService snapshotService = new SnapshotService(properties.getSourceRoot(), loadLatestFileSystemState());
        snapshotService.setMessageConsumer(messageConsumer);
        FileSystemState newState = snapshotService.createNewSnapshot(newSnapshotDir);
        writeLatestFileSystemState(newState.withRootLocation(newSnapshotDir));
        properties = properties.getNewUpdated(newSnapshotDir);
    }

    /**
     * Intended to reproduce a file system state of an older snapshot or to repair a broken file system state.
     * @param sourceDir The directory to compute the new state from.
     */
    public void recomputeFileSystemState(Path sourceDir) {
        FileSystemState fileSystemState = computeFileSystemState(Root.from(sourceDir));
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

    private FileSystemState computeFileSystemState(Root root) {
        FileSystemState.Builder builder = FileSystemState.builder(root.rootDirLocation());
        try (Stream<Path> files = Files.walk(root.pathToRootDir(), FileVisitOption.FOLLOW_LINKS)) {
            files
                    .filter(Files::isRegularFile)
                    .map(absPath -> fileState(root.rootDirLocation(), absPath))
                    .forEach(builder::add);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not iterate over directory contents at " + properties.getSourceRoot() + ": " + e.getMessage(), e);
        }
        return builder.build();
    }

    private FileState fileState(Path rootToRelativizeAgainst, Path absPath) {
        Instant lastModified;
        try {
            lastModified = Files.getLastModifiedTime(absPath).toInstant();
        } catch (IOException e) {
            messageConsumer.consumeMessage(Message.error("Could not read last modified from %s: %s".formatted(absPath, e.getMessage())), e);
            lastModified = Instant.now();
        }
        CheckpointChecksum checksum;
        try {
            checksum = CheckpointChecksum.from(Files.newInputStream(absPath));
        } catch (IOException e) {
            messageConsumer.consumeMessage(Message.error("Could not create checksum from %s: %s".formatted(absPath, e.getMessage())), e);
            checksum = CheckpointChecksum.undefined();
        }
        return new FileState(rootToRelativizeAgainst.relativize(absPath), lastModified, checksum);
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
        fileSystemState.write(properties.getSnapshotsHomeDir().resolve(LATEST_FILE_STATE_FILE_NAME));
    }

}

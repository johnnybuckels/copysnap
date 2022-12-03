package johnny.buckels.copysnap.model;

import johnny.buckels.copysnap.service.SnapshotService;
import johnny.buckels.copysnap.service.hashing.ParallelHashingService;
import johnny.buckels.copysnap.service.logging.AbstractMessageProducer;
import johnny.buckels.copysnap.service.logging.Message;
import johnny.buckels.copysnap.service.logging.MessageConsumer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class Context extends AbstractMessageProducer {

    private static final String LATEST_FILE_STATE_FILE_NAME = ".latest";

    private final Path sourceDir;
    private final Path homeDir;
    private final ZonedDateTime created;
    private ZonedDateTime lastSnapshot;

    private double cpuParallelism = 1.0;

    Context(Path sourceDir, Path homeDir, ZonedDateTime created, ZonedDateTime lastSnapshot) {
        this.sourceDir = sourceDir;
        this.homeDir = homeDir;
        this.created = created;
        this.lastSnapshot = lastSnapshot;
    }

    /**
     * @return the path to the newly created snapshot
     */
    public Path createSnapshot() {
        FileSystemState newState = computeFileSystemState(sourceDir);
        Path newSnapshotDir = homeDir.resolve(generateSnapshotName());

        SnapshotService snapshotService = new SnapshotService(newState, loadLatestFileSystemState());
        snapshotService.setMessageConsumer(messageConsumer);
        snapshotService.createNewSnapshot(newSnapshotDir);
        lastSnapshot = ZonedDateTime.now();
        writeLatestFileSystemState(newState.switchRootTo(newSnapshotDir));
        return newSnapshotDir;
    }

    /**
     * Intended to reproduce a file system state of an older snapshot or to repair a broken file system state.
     */
    public void recomputeFileSystemStateAndSave(Path sourceDir) {
        FileSystemState fileSystemState = computeFileSystemState(sourceDir);
        writeLatestFileSystemState(fileSystemState);
    }

    public Path getSourceDir() {
        return sourceDir;
    }

    public Path getHomeDir() {
        return homeDir;
    }

    public ZonedDateTime getCreated() {
        return created;
    }

    public ZonedDateTime getLastSnapshot() {
        return lastSnapshot;
    }

    public Context withMessageConsumer(MessageConsumer messageConsumer) {
        setMessageConsumer(Objects.requireNonNull(messageConsumer));
        return this;
    }

    public Context withCpuParallelism(double cpuParallelism) {
        this.cpuParallelism = cpuParallelism;
        return this;
    }

    private FileSystemState computeFileSystemState(Path sourceDir) {
        ParallelHashingService parallelHashingService = new ParallelHashingService(computePoolSize());
        parallelHashingService.setMessageConsumer(messageConsumer);
        return parallelHashingService.computeState(sourceDir);
    }

    private String generateSnapshotName() {
        return ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
    }

    private FileSystemState loadLatestFileSystemState() {
        messageConsumer.consumeMessage(Message.info("Loading latest file system state."));
        Path latestSnapshotFile = homeDir.resolve(LATEST_FILE_STATE_FILE_NAME);
        if (Files.isRegularFile(latestSnapshotFile)) {
            FileSystemState fileSystemState = FileSystemState.read(latestSnapshotFile);
            messageConsumer.consumeMessage(Message.info("Successfully loaded snapshot "
                    + fileSystemState.getCreated().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
            return fileSystemState;
        }
        else {
            messageConsumer.consumeMessage(Message.info("No latest file system state found."));
            return FileSystemState.empty();
        }
    }

    private void writeLatestFileSystemState(FileSystemState fileSystemState) {
        messageConsumer.consumeMessage(Message.info("Writing latest file system state."));
        fileSystemState.writeTo(homeDir.resolve(LATEST_FILE_STATE_FILE_NAME));
    }

    private int computePoolSize() {
        return (int) Math.round(Math.max(1, Runtime.getRuntime().availableProcessors() * Math.min(1, cpuParallelism)));
    }

    public String toDisplayString() {
        return String.format("%16s: %s", "source", sourceDir) +
                "\n" + String.format("%16s: %s", "home", homeDir) +
                "\n" + String.format("%16s: %s", "created", created.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)) +
                "\n" + String.format("%16s: %s", "last snapshot", lastSnapshot == null ? "" : lastSnapshot.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

}

package johnny.buckels.copysnap.model;

import johnny.buckels.copysnap.service.diffing.FileSystemAccessor;
import johnny.buckels.copysnap.service.diffing.FileSystemDiffService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

// TODO: Logging
public record Context(ContextProperties properties) {

    public Context createSnapshot() {
        Path newSnapshotDir = properties.snapshotsHomeDir().resolve(generateSnapshotName());
        Root sourceRoot = properties.sourceRoot();
        ContextProperties enrichedProperties = properties.fullyLoadLatestFileState();

//        messageConsumer.consumeMessage(Message.info("Creating new snapshot at " + newSnapshotDir));
        FileSystemAccessor fsa = FileSystemAccessor.newDefaultAccessor();
        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(fsa);
//        fileSystemDiffService.setMessageConsumer(messageConsumer);
        FileSystemState newState = fileSystemDiffService
                .computeDiff(sourceRoot, enrichedProperties.latestState())
                .computeCopyActions(newSnapshotDir)
                .apply(fsa)
                .withRootLocation(newSnapshotDir);  // TODO: Remove necessity to add root to fss
        ContextProperties updatedProperties = properties
                .withFileSystemState(newState)
                .writeAndGet();
        return new Context(updatedProperties);
    }

    /**
     * Intended to reproduce a file system state of an older snapshot or to repair a broken file system state.
     * @param sourceDir The directory to compute the new state from.
     */
    public Context recomputeFileSystemState(Path sourceDir) {
        Root rootToComputeStateFrom = Root.from(sourceDir);
        FileSystemState.Builder builder = FileSystemState.builder(rootToComputeStateFrom.rootDirLocation());
        try (Stream<Path> files = Files.walk(rootToComputeStateFrom.pathToRootDir(), FileVisitOption.FOLLOW_LINKS)) {
            files
                    .filter(Files::isRegularFile)
                    .map(absPath -> fileState(rootToComputeStateFrom.rootDirLocation(), absPath))
                    .forEach(builder::add);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not iterate over directory contents at " + rootToComputeStateFrom.pathToRootDir() + ": " + e.getMessage(), e);
        }
        FileSystemState newFss = builder.build();
        ContextProperties updatedProperties = properties
                .withFileSystemState(newFss)
                .writeAndGet();
        return new Context(updatedProperties);
    }

    public Context writeAndGet() {
        return new Context(properties.writeAndGet());
    }

    public Path getContextHome() {
        return properties.snapshotsHomeDir();
    }

    public String toDisplayString() {
        return properties.toDisplayString();
    }

    private FileState fileState(Path rootToRelativizeAgainst, Path absPath) {
        Instant lastModified;
        try {
            lastModified = Files.getLastModifiedTime(absPath).toInstant();
        } catch (IOException e) {
//            messageConsumer.consumeMessage(Message.error("Could not read last modified from %s: %s".formatted(absPath, e.getMessage())), e);
            lastModified = Instant.now();
        }
        CheckpointChecksum checksum;
        try {
            checksum = CheckpointChecksum.from(Files.newInputStream(absPath));
        } catch (IOException e) {
//            messageConsumer.consumeMessage(Message.error("Could not create checksum from %s: %s".formatted(absPath, e.getMessage())), e);
            checksum = CheckpointChecksum.undefined();
        }
        return new FileState(rootToRelativizeAgainst.relativize(absPath), lastModified, checksum);
    }

    private String generateSnapshotName() {
        return ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
    }

}

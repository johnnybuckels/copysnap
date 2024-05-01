package johnny.buckels.copysnap.model;

import johnny.buckels.copysnap.service.diffing.FileSystemAccessor;
import johnny.buckels.copysnap.service.diffing.FileSystemDiffService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

// TODO: Logging
public record Context(ContextProperties properties, FileSystemState latest) {

    static final String CONTEXT_PROPERTIES_FILE_NAME = "context.properties";
    private static final String LATEST_FILE_STATE_FILE_NAME = ".latest";
    private static final OpenOption[] CREATE_OVERWRITE_OPEN_OPTIONS = {StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};

    public Context createSnapshot() {
        if (latest == null)
            throw new IllegalStateException("Can not create snapshot without a loaded latest file system state.");
        Path newSnapshotDir = properties.snapshotsHomeDir().resolve(generateSnapshotName());
        Path latestRootLocation = properties.snapshotProperties().rootLocation();
//        messageConsumer.consumeMessage(Message.info("Creating new snapshot at " + newSnapshotDir));
//        fileSystemDiffService.setMessageConsumer(messageConsumer);
        FileSystemAccessor fsa = FileSystemAccessor.newDefaultAccessor();
        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(fsa);
        FileSystemState newState = fileSystemDiffService
                .computeDiff(properties.source(), latest)
                .computeCopyActions(newSnapshotDir, latestRootLocation)
                .apply(fsa);
        ContextProperties updatedProperties = properties
                .withSnapshotProperties(new ContextProperties.SnapshotProperties(newSnapshotDir, ZonedDateTime.now(), newState.fileCount()));
        return new Context(updatedProperties, newState);
    }

    public Context loadLatestSnapshot() throws ContextIOException {
        Path latestSnapshotFile = properties.snapshotsHomeDir().resolve(LATEST_FILE_STATE_FILE_NAME);
        if (!Files.isRegularFile(latestSnapshotFile)) {
            // TODO: Logging
            return new Context(properties, FileSystemState.empty());
        }
        FileSystemState fss;
        try (InputStream is = Files.newInputStream(latestSnapshotFile)) {
            fss = FileSystemState.read(is);
        } catch (IOException e) {
            throw new ContextIOException("Could not read latest FileSystemState from %s: %s".formatted(latestSnapshotFile, e.getMessage()), e);
        }
        return new Context(properties, fss);
    }

    /**
     * Intended to reproduce a file system state of an older snapshot or to repair a broken file system state.
     * @param sourceDir The directory to compute the new state from.
     */
    public Context recomputeFileSystemState(Path sourceDir) throws ContextIOException {
        Root rootToComputeStateFrom = Root.from(sourceDir);
        FileSystemState.Builder builder = FileSystemState.builder();
        try (Stream<Path> files = Files.walk(rootToComputeStateFrom.pathToRootDir(), FileVisitOption.FOLLOW_LINKS)) {
            files
                    .filter(Files::isRegularFile)
                    .map(absPath -> readFileState(rootToComputeStateFrom.rootDirLocation(), absPath))
                    .forEach(builder::add);
        } catch (IOException e) {
            throw new ContextIOException("Could not iterate over directory contents at " + rootToComputeStateFrom.pathToRootDir() + ": " + e.getMessage(), e);
        }
        FileSystemState newFss = builder.build();
        ContextProperties updatedProperties = properties
                .withSnapshotProperties(new ContextProperties.SnapshotProperties(rootToComputeStateFrom.rootDirLocation(), ZonedDateTime.now(), newFss.fileCount()));
        return new Context(updatedProperties, newFss);
    }

    public void write() throws ContextIOException {
        try {
            Files.createDirectories(properties.snapshotsHomeDir());
        } catch (IOException e) {
            throw new ContextIOException("Could not create snapshot home directories at %s: %s".formatted(properties.snapshotsHomeDir(), e.getMessage()), e);
        }
        Path latestStateFile = properties.snapshotsHomeDir().resolve(LATEST_FILE_STATE_FILE_NAME);
        try (OutputStream fileSystemStateOs = Files.newOutputStream(latestStateFile, CREATE_OVERWRITE_OPEN_OPTIONS)) {
            latest.write(fileSystemStateOs);
        } catch (IOException e) {
            throw new ContextIOException("Could not write latest file states to %s: %s".formatted(latestStateFile, e.getMessage()), e);
        }
        Path propertiesFile = properties.snapshotsHomeDir().resolve(CONTEXT_PROPERTIES_FILE_NAME);
        try (OutputStream propertiesOs = Files.newOutputStream(propertiesFile, CREATE_OVERWRITE_OPEN_OPTIONS)) {
            properties.toProperties()
                    .store(propertiesOs, "CopySnap properties at %s".formatted(properties.snapshotsHomeDir()));
        } catch (IOException e) {
            throw new ContextIOException("Could not write context properties to %s: %s".formatted(propertiesFile, e.getMessage()), e);
        }
    }

    public Path getContextHome() {
        return properties.snapshotsHomeDir();
    }

    public String toDisplayString() {
        return properties.toDisplayString();
    }

    private FileState readFileState(Path rootToRelativizeAgainst, Path absPath) {
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

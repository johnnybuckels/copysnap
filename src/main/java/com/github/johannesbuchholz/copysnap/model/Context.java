package com.github.johannesbuchholz.copysnap.model;

import com.github.johannesbuchholz.copysnap.service.diffing.FileSystemAccessor;
import com.github.johannesbuchholz.copysnap.service.diffing.FileSystemDiff;
import com.github.johannesbuchholz.copysnap.service.diffing.FileSystemDiffService;
import com.github.johannesbuchholz.copysnap.service.logging.AbstractLogProducer;
import com.github.johannesbuchholz.copysnap.service.logging.FilePrintingLogConsumer;
import com.github.johannesbuchholz.copysnap.service.logging.LogConsumer;
import com.github.johannesbuchholz.copysnap.service.logging.Level;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class Context extends AbstractLogProducer {

    private final ContextProperties properties;
    private final FileSystemState latest;

    static final String CONTEXT_PROPERTIES_FILE_NAME = "context.properties";
    private static final String LATEST_FILE_STATE_FILE_NAME = ".latest";
    private static final OpenOption[] CREATE_OVERWRITE_OPEN_OPTIONS = {StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};

    Context(ContextProperties properties, FileSystemState latest) {
        this(properties, latest, new HashSet<>());
    }

    Context(ContextProperties properties, FileSystemState latest, Set<LogConsumer> logConsumers) {
        super(logConsumers);
        this.properties = properties;
        this.latest = latest;
    }

    public Context createSnapshot() throws ContextIOException {
        if (latest == null)
            throw new IllegalStateException("Can not create snapshot without a loaded latest file system state.");
        Path newSnapshotDir = properties.snapshotsHomeDir().resolve(generateSnapshotName());
        Path latestRootLocation = properties.snapshotProperties().rootLocation();

        FileSystemAccessor fsa = FileSystemAccessor.newDefaultAccessor();
        try {
            fsa.createDirectories(newSnapshotDir);
        } catch (IOException e) {
            throw new ContextIOException("Coulkd not create new snapshot directory at %s: %s".formatted(newSnapshotDir, e.getMessage()), e);
        }

        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(fsa);
        Context newSnapshotContext;
        try (FilePrintingLogConsumer report = FilePrintingLogConsumer.at(newSnapshotDir.resolve("report.txt"))) {
            addConsumer(report);
            logConsumers.forEach(fileSystemDiffService::addConsumer);
            FileSystemDiff.Actions copyActions = fileSystemDiffService
                    .computeDiff(properties.source(), latest)
                    .computeCopyActions(newSnapshotDir, latestRootLocation);
            logConsumers.forEach(copyActions::addConsumer);

            FileSystemState newState = copyActions.apply(fsa);
            ContextProperties updatedProperties = properties.withSnapshotProperties(new ContextProperties.SnapshotProperties(newSnapshotDir, Instant.now(), newState.fileCount()));

            newSnapshotContext = new Context(updatedProperties, newState, logConsumers);
            removeConsumer(report);
        } catch (IOException e) {
            throw new ContextIOException("Could not create snapshot: " + e.getMessage(), e);
        }
        return newSnapshotContext;
    }

    public Context loadLatestSnapshot() throws ContextIOException {
        Path latestSnapshotFile = properties.snapshotsHomeDir().resolve(LATEST_FILE_STATE_FILE_NAME);
        if (!Files.isRegularFile(latestSnapshotFile)) {
            log(Level.INFO, "Could not find latest snapshot at %s. Loading with empty file system state.".formatted(latestSnapshotFile));
            ContextProperties newProperties = properties.withSnapshotProperties(ContextProperties.SnapshotProperties.EMPTY);
            return new Context(newProperties, FileSystemState.empty(), logConsumers);
        }
        FileSystemState fss;
        try (InputStream is = Files.newInputStream(latestSnapshotFile)) {
            fss = FileSystemState.read(is);
        } catch (IOException e) {
            throw new ContextIOException("Could not read latest FileSystemState from %s: %s".formatted(latestSnapshotFile, e.getMessage()), e);
        }
        return new Context(properties, fss, logConsumers);
    }

    /**
     * Intended to reproduce a file system state of an older snapshot or to repair a broken file system state.
     * @param sourceDir The directory to compute the new state from.
     */
    public Context recomputeFileSystemState(Path sourceDir) throws ContextIOException {
        Root rootToComputeStateFrom = Root.from(sourceDir);
        log(Level.DEBUG, "Recomputing file system state at %s".formatted(rootToComputeStateFrom));
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
                .withSnapshotProperties(new ContextProperties.SnapshotProperties(rootToComputeStateFrom.rootDirLocation(), Instant.now(), newFss.fileCount()));
        return new Context(updatedProperties, newFss, logConsumers);
    }

    public void write() throws ContextIOException {
        try {
            Files.createDirectories(properties.snapshotsHomeDir());
        } catch (IOException e) {
            throw new ContextIOException("Could not create snapshot home directories at %s: %s".formatted(properties.snapshotsHomeDir(), e.getMessage()), e);
        }
        Path propertiesFile = properties.snapshotsHomeDir().resolve(CONTEXT_PROPERTIES_FILE_NAME);
        try (OutputStream propertiesOs = Files.newOutputStream(propertiesFile, CREATE_OVERWRITE_OPEN_OPTIONS)) {
            properties.toProperties()
                    .store(propertiesOs, "CopySnap properties at %s".formatted(properties.snapshotsHomeDir()));
        } catch (IOException e) {
            throw new ContextIOException("Could not write context properties to %s: %s".formatted(propertiesFile, e.getMessage()), e);
        }

        if (latest != null) {
            Path latestStateFile = properties.snapshotsHomeDir().resolve(LATEST_FILE_STATE_FILE_NAME);
            try (OutputStream fileSystemStateOs = Files.newOutputStream(latestStateFile, CREATE_OVERWRITE_OPEN_OPTIONS)) {
                latest.write(fileSystemStateOs);
            } catch (IOException e) {
                throw new ContextIOException("Could not write latest file states to %s: %s".formatted(latestStateFile, e.getMessage()), e);
            }
        } else {
            log(Level.DEBUG, "Writing context info: No latest file system state");
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
            log(Level.ERROR, "Could not read last modified from %s: %s".formatted(absPath, e.getMessage()));
            logStacktrace(Level.DEBUG, e);
            lastModified = Instant.now();
        }
        CheckpointChecksum checksum;
        try {
            checksum = CheckpointChecksum.from(Files.newInputStream(absPath));
        } catch (IOException e) {
            log(Level.ERROR, "Could not create checksum from %s: %s".formatted(absPath, e.getMessage()));
            logStacktrace(Level.DEBUG, e);
            checksum = CheckpointChecksum.undefined();
        }
        return new FileState(rootToRelativizeAgainst.relativize(absPath), lastModified, checksum);
    }

    private String generateSnapshotName() {
        return ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
    }

}

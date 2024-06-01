package com.github.johannesbuchholz.copysnap.model;

import com.github.johannesbuchholz.copysnap.Main;
import com.github.johannesbuchholz.copysnap.service.diffing.FileSystemAccessor;
import com.github.johannesbuchholz.copysnap.service.diffing.FileSystemDiff;
import com.github.johannesbuchholz.copysnap.service.diffing.FileSystemDiffService;
import com.github.johannesbuchholz.copysnap.service.logging.AbstractLogProducer;
import com.github.johannesbuchholz.copysnap.service.logging.FilePrintingLogConsumer;
import com.github.johannesbuchholz.copysnap.service.logging.Level;
import com.github.johannesbuchholz.copysnap.service.logging.LogConsumer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Optional;
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
            throw new ContextIOException("Could not create new snapshot directory at %s: %s".formatted(newSnapshotDir, e.getMessage()), e);
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
            ContextProperties updatedProperties = properties.withSnapshotProperties(new ContextProperties.SnapshotProperties(newSnapshotDir, ZonedDateTime.now(), newState.fileCount()));

            newSnapshotContext = new Context(updatedProperties, newState, logConsumers);
            removeConsumer(report);
        } catch (IOException e) {
            throw new ContextIOException("Could not create snapshot: " + e.getMessage(), e);
        }
        return newSnapshotContext;
    }

    public Context loadLatestSnapshot() throws ContextIOException {
        Path latestSnapshotFile = properties.snapshotsHomeDir().resolve(LATEST_FILE_STATE_FILE_NAME);
        ZonedDateTime start = ZonedDateTime.now();
        logTaskStart(Level.INFO, "Loading latest snapshot file system state", start, "from", latestSnapshotFile);
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
        logTaskEnd(Level.INFO, "Done loading latest snapshot file system state", Duration.between(start, ZonedDateTime.now()));
        return new Context(properties, fss, logConsumers);
    }

    /**
     * Intended to reproduce a file system state of an older snapshot or to repair a broken file system state.
     * @param sourceDir The directory to compute the new state from.
     */
    public Context recomputeFileSystemState(Path sourceDir) throws ContextIOException {
        Root rootToComputeStateFrom = Root.from(sourceDir);
        ZonedDateTime start = ZonedDateTime.now();
        logTaskStart(Level.INFO, "Recomputing file system state", start, "from", rootToComputeStateFrom);
        FileSystemState.Builder builder = FileSystemState.builder();
        try (Stream<Path> files = Files.walk(rootToComputeStateFrom.pathToRootDir(), FileVisitOption.FOLLOW_LINKS)) {
            files
                    .filter(Files::isRegularFile)
                    .map(absPath -> FileState.readFileState(rootToComputeStateFrom.rootDirLocation(), absPath))
                    .forEach(builder::add);
        } catch (IOException e) {
            throw new ContextIOException("Could not iterate over directory contents at " + rootToComputeStateFrom.pathToRootDir() + ": " + e.getMessage(), e);
        }
        FileSystemState newFss = builder.build();
        ContextProperties updatedProperties = properties
                .withSnapshotProperties(new ContextProperties.SnapshotProperties(rootToComputeStateFrom.rootDirLocation(), ZonedDateTime.now(), newFss.fileCount()));
        logTaskEnd(Level.INFO, "Done recomputing file system state", Duration.between(start, ZonedDateTime.now()));
        return new Context(updatedProperties, newFss, logConsumers);
    }

    public void write() throws ContextIOException {
        ZonedDateTime start = ZonedDateTime.now();
        Path propertiesFile = properties.snapshotsHomeDir().resolve(CONTEXT_PROPERTIES_FILE_NAME);
        logTaskStart(Level.DEBUG, "Start writing context", start, "at", propertiesFile);
        try {
            Files.createDirectories(propertiesFile.getParent());
        } catch (IOException e) {
            throw new ContextIOException("Could not create snapshot home directories at %s: %s".formatted(properties.snapshotsHomeDir(), e.getMessage()), e);
        }
        try (OutputStream propertiesOs = Files.newOutputStream(propertiesFile, CREATE_OVERWRITE_OPEN_OPTIONS)) {
            properties.toProperties()
                    .store(propertiesOs, "CopySnap properties at %s written with version %s".formatted(properties.snapshotsHomeDir(), Main.APP_VERSION));
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
            log(Level.DEBUG, "Writing context properties: No latest file system state");
        }
        logTaskEnd(Level.DEBUG, "Done writing context properties", Duration.between(start, ZonedDateTime.now()));
    }

    public Path getContextHome() {
        return properties.snapshotsHomeDir();
    }

    public Optional<Path> getLatestSnapshotLocation() {
        return Optional.ofNullable(properties.snapshotProperties())
                .map(ContextProperties.SnapshotProperties::rootLocation);
    }

    public String toDisplayString() {
        return properties.toDisplayString();
    }

    private String generateSnapshotName() {
        return ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
    }

}

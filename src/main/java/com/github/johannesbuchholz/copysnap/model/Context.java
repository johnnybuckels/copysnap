package com.github.johannesbuchholz.copysnap.model;

import com.github.johannesbuchholz.copysnap.logging.*;
import com.github.johannesbuchholz.copysnap.model.state.FileState;
import com.github.johannesbuchholz.copysnap.model.state.FileSystemState;
import com.github.johannesbuchholz.copysnap.service.diffing.FileSystemAccessor;
import com.github.johannesbuchholz.copysnap.service.diffing.FileSystemDiff;
import com.github.johannesbuchholz.copysnap.service.diffing.FileSystemDiffService;
import com.github.johannesbuchholz.copysnap.service.diffing.copy.CopyAction;
import com.github.johannesbuchholz.copysnap.service.diffing.copy.PlainCopyAction;
import com.github.johannesbuchholz.copysnap.util.TimeUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Context extends AbstractLogProducer {

    /*
    We separated the actual file system state from the context and snapshot properties as loading the potentially large
    file system state might be too resource consuming and even unwanted for certain operations.
     */
    private final ContextProperties properties;
    // nullable
    private final FileSystemState latest;

    Context(ContextProperties properties, FileSystemState latest) {
        this(properties, latest, new HashSet<>());
    }

    Context(ContextProperties properties, FileSystemState latest, Set<LogConsumer> logConsumers) {
        super(logConsumers);
        this.properties = properties;
        this.latest = latest;
    }

    public Context createSnapshot() {
        if (latest == null)
            throw new IllegalStateException("Can not create snapshot without a loaded latest file system state.");
        SnapshotName snapshotName = SnapshotName.getNew();
        ZonedDateTime start = snapshotName.created();
        Path newSnapshotDir = properties.snapshotsHomeDir().resolve(snapshotName.asString());

        Path latestRootLocation = null;
        if (properties.snapshotProperties() != null) {
            // here if there has been an earlier snapshot.
            latestRootLocation = properties.snapshotProperties().rootDirLocation();
        }

        FileSystemAccessor fsa = FileSystemAccessor.newDefaultAccessor();
        try {
            fsa.createDirectories(newSnapshotDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        FileSystemState newState;
        try (FilePrintingLogConsumer report = FilePrintingLogConsumer.at(newSnapshotDir.resolve("report.txt"))) {
            addConsumer(report);
            logTaskStart(Level.INFO, "Creating new snapshot", start, "at", newSnapshotDir);

            FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(fsa);
            logConsumers.forEach(fileSystemDiffService::addConsumer);
            FileSystemDiff.Actions copyActions = fileSystemDiffService
                    .computeDiff(properties.source(), latest)
                    .computeCopyActions(newSnapshotDir, latestRootLocation);
            logConsumers.forEach(copyActions::addConsumer);
            newState = copyActions.apply(fsa);
        } catch (IOException e) {
            String errorMsg = "Could not create snapshot: " + e.getMessage();
            log(Level.ERROR, errorMsg);
            logStacktrace(Level.ERROR, e);
            throw new ContextIOException(errorMsg, e);
        }
        ContextProperties updatedProperties = properties.withSnapshotProperties(
                new ContextProperties.SnapshotProperties(newSnapshotDir, snapshotName.created(), newState.fileCount()));

        logTaskEnd(Level.INFO, "Done creating new snapshot", Duration.between(start, ZonedDateTime.now()));
        return new Context(updatedProperties, newState, logConsumers);
    }

    public Context loadLatestSnapshot() {
        Path latestSnapshotFile = properties.snapshotsHomeDir().resolve(Contexts.LATEST_FILE_STATE_FILE_NAME);
        ZonedDateTime start = ZonedDateTime.now();
        logTaskStart(Level.INFO, "Loading latest snapshot file system state", start, "from", latestSnapshotFile);
        if (!Files.isRegularFile(latestSnapshotFile)) {
            log(Level.INFO, "Could not find latest snapshot at %s. Loading with empty file system state.".formatted(latestSnapshotFile));
            ContextProperties newProperties = properties.withSnapshotProperties(null);
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
    public Context recomputeFileSystemState(Path sourceDir) {
        Root rootToComputeStateFrom = Root.from(sourceDir);
        ZonedDateTime start = ZonedDateTime.now();
        logTaskStart(Level.INFO, "Recomputing file system state", start, "from", rootToComputeStateFrom.pathToRootDir());
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

    public Context solidify() {
        if (properties.snapshotProperties() == null) {
            log(Level.INFO, "Unable to solidify context: No latest snapshot available.");
            return this;
        }
        Path latestSnapshotRootLocation = properties.snapshotProperties().rootDirLocation();

        SnapshotName snapshotName = SnapshotName.getNew().withSuffix("s");
        Path newSnapshotDir = properties.snapshotsHomeDir().resolve(snapshotName.asString());

        FileSystemAccessor fsa = FileSystemAccessor.newDefaultAccessor();
        try {
            fsa.createDirectories(newSnapshotDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        ZonedDateTime start = snapshotName.created();
        try (FilePrintingLogConsumer report = FilePrintingLogConsumer.at(newSnapshotDir.resolve("report.txt"))) {
            addConsumer(report);
            logTaskStart(Level.INFO, "Solidifying snapshots", start, "at", newSnapshotDir, "source snapshot", latestSnapshotRootLocation);

            List<PlainCopyAction> copyActions;
            try (Stream<Path> files = Files.walk(latestSnapshotRootLocation.resolve(properties.source().retrieveName()), FileVisitOption.FOLLOW_LINKS)) {
                copyActions = files
                        .filter(Files::isRegularFile)
                        .map(p -> new PlainCopyAction(
                                latestSnapshotRootLocation,
                                newSnapshotDir,
                                latestSnapshotRootLocation.relativize(p)))
                        .toList();
            }
            ProgressConsolePrinter progressConsolePrinter = new ProgressConsolePrinter("Copying files");
            int performedCount = 0;
            progressConsolePrinter.update(performedCount, copyActions.size());
            for (CopyAction copyAction : copyActions) {
                log(Level.DEBUG, "Apply %s".formatted(copyAction));
                try {
                    // we do not store file states as we only copy already known files from existing snapshots
                    copyAction.perform(fsa);
                } catch (IOException e) {
                    String errorMsg = "Could not apply copy action " + copyAction + ": " + e;
                    log(Level.ERROR, errorMsg);
                    logStacktrace(Level.DEBUG, e);
                }
                progressConsolePrinter.update(++performedCount, copyActions.size());
            }
            progressConsolePrinter.newLine();
        } catch (IOException e) {
            String errorMsg = "Could not solidify snapshots: " + e.getMessage();
            log(Level.ERROR, errorMsg);
            logStacktrace(Level.ERROR, e);
            throw new ContextIOException(errorMsg, e);
        }

        ContextProperties.SnapshotProperties snapshotProperties = new ContextProperties.SnapshotProperties(
                newSnapshotDir, snapshotName.created(), properties.snapshotProperties().fileCount());
        ContextProperties newContextProperties = properties.withSnapshotProperties(snapshotProperties);

        logTaskEnd(Level.INFO, "Done solidifying snapshots", Duration.between(start, ZonedDateTime.now()));
        // latest fss does not change as we did not read any new files from the source directory.
        return new Context(newContextProperties, latest, logConsumers);
    }

    FileSystemState getLatestFileSystemState() {
        return latest;
    }

    ContextProperties getProperties() {
        return properties;
    }

    public Path getContextHome() {
        return properties.snapshotsHomeDir();
    }

    public String toDisplayString() {
        return properties.toDisplayString();
    }

    private record SnapshotName(ZonedDateTime created, String suffix) {

        static SnapshotName getNew() {
            return new SnapshotName(ZonedDateTime.now(), null);
        }

        SnapshotName withSuffix(String suffix) {
            return new SnapshotName(created, suffix);
        }

        String asString() {
            return Stream.of(TimeUtils.asString(created), suffix)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("-"));
        }

    }
}

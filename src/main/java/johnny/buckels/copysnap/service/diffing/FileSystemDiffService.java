package johnny.buckels.copysnap.service.diffing;

import johnny.buckels.copysnap.model.FileState;
import johnny.buckels.copysnap.model.FileSystemState;
import johnny.buckels.copysnap.model.Root;
import johnny.buckels.copysnap.service.logging.AbstractLogProducer;
import johnny.buckels.copysnap.service.logging.Level;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class FileSystemDiffService extends AbstractLogProducer {

    private final FileSystemAccessor fileSystemAccessor;

    public FileSystemDiffService(FileSystemAccessor fileSystemAccessor) {
        this.fileSystemAccessor = fileSystemAccessor;
    }

    private enum FileChangeState {UNCHANGED, CHANGED, NEW, ERROR}

    /**
     * This method accesses the file system.
     *
     * @param sourceRoot The root object to take a snapshot from.
     */
    public FileSystemDiff computeDiff(Root sourceRoot, FileSystemState oldSystemState) throws IOException {
        AtomicInteger newCount = new AtomicInteger();
        AtomicInteger changedCount = new AtomicInteger();
        AtomicInteger unchangedCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();
        Set<Path> processedNewFiles = new HashSet<>();
        FileSystemNode systemDiffTree = FileSystemNode.getNew();

        ZonedDateTime start = ZonedDateTime.now();
        log(Level.INFO, "Computing file differences - started: %s, at: %s)".formatted(start.toLocalDateTime().truncatedTo(ChronoUnit.SECONDS), sourceRoot.pathToRootDir()));
        try (Stream<Path> paths = fileSystemAccessor.findFiles(sourceRoot.pathToRootDir())) {
            paths
                .map(p -> sourceRoot.rootDirLocation().relativize(p))
                .forEach(currentNewPath -> {
                    // determine changed existing changed files
                    FileSystemNode newNode = systemDiffTree.insert(currentNewPath);
                    switch (determineChange(oldSystemState, sourceRoot.rootDirLocation(), currentNewPath)) {
                        case UNCHANGED -> unchangedCount.getAndIncrement();
                        case CHANGED -> {
                            changedCount.getAndIncrement();
                            newNode.markAsChanged();
                        }
                        case NEW -> {
                            newCount.getAndIncrement();
                            newNode.markAsChanged();
                        }
                        case ERROR -> {
                            errorCount.getAndIncrement();
                            newNode.markAsChanged();
                        }
                    }
                    processedNewFiles.add(currentNewPath);
                });
        }

        // determine no longer present files and mark former containing directories as changed
        FileSystemState remainingStates = oldSystemState.newByRemovingMissing(processedNewFiles);
        FileSystemState removedStates = oldSystemState.newBySetMinus(remainingStates);
        for (Path noLongerPresentPath : removedStates.paths()) {
            systemDiffTree.getDeepestKnownAlong(noLongerPresentPath).markAsChanged();
        }

        int removedCount = removedStates.fileCount();
        log(Level.INFO, "File differences: %s new, %s changed, %s removed, %s unchanged, %s erroneous"
                .formatted(newCount, changedCount, removedCount, unchangedCount, errorCount));
        log(Level.INFO, "Done computing file differences (%s ms)".formatted(Duration.between(start, ZonedDateTime.now()).toMillis()));
        return new FileSystemDiff(
                sourceRoot,
                remainingStates,
                systemDiffTree,
                new FileSystemDiff.DiffCounts(newCount.get(), removedCount, changedCount.get(), unchangedCount.get(), errorCount.get())
        );
    }

    private FileChangeState determineChange(FileSystemState oldSystemState, Path root, Path newRelFilePath) {
        Path newAbsFilePath = root.resolve(newRelFilePath);
        Instant newLastModified;
        try {
            newLastModified = fileSystemAccessor.getLastModifiedTime(newAbsFilePath);
        } catch (IOException e) {
            String errorMsg = "Could not determine last modified time at %s: %s".formatted(newAbsFilePath, e.getMessage());
            log(Level.ERROR, errorMsg);
            logStacktrace(Level.DEBUG, e);
            return FileChangeState.ERROR;
        }
        Optional<FileState> lastCapturedState = oldSystemState.get(newRelFilePath);
        if (lastCapturedState.isPresent()) {
            FileState oldFileState = lastCapturedState.get();
            if (newLastModified.isAfter(oldFileState.getLastModified())) {
                boolean hasChecksumChanged;
                try {
                    hasChecksumChanged = !fileSystemAccessor.areChecksumsEqual(oldFileState.getChecksum(), newAbsFilePath);
                } catch (IOException e) {
                    String errorMsg = "Could not determine hash at %s: %s".formatted(newAbsFilePath, e.getMessage());
                    log(Level.ERROR, errorMsg);
                    logStacktrace(Level.DEBUG, e);
                    return FileChangeState.ERROR;
                }
                if (hasChecksumChanged) {
                    log(Level.DEBUG, FileChangeState.CHANGED + ": " + newAbsFilePath);
                    return FileChangeState.CHANGED;
                }
            }
            // we assume that newModified less or equal oldModified indicates an unchanged file
        } else {
            log(Level.DEBUG, FileChangeState.NEW + ": " + newAbsFilePath);
            return FileChangeState.NEW;
        }
        log(Level.DEBUG, FileChangeState.UNCHANGED + ": " + newAbsFilePath);
        return FileChangeState.UNCHANGED;
    }

}

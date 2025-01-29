package com.github.johannesbuchholz.copysnap.service.diffing;

import com.github.johannesbuchholz.copysnap.logging.AbstractLogProducer;
import com.github.johannesbuchholz.copysnap.logging.Level;
import com.github.johannesbuchholz.copysnap.model.Root;
import com.github.johannesbuchholz.copysnap.model.state.FileState;
import com.github.johannesbuchholz.copysnap.model.state.FileSystemState;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class FileSystemDiffService extends AbstractLogProducer {

    private final FileSystemAccessor fileSystemAccessor;

    public FileSystemDiffService(FileSystemAccessor fileSystemAccessor) {
        this.fileSystemAccessor = fileSystemAccessor;
    }

    /**
     * This method accesses the file system.
     *
     * @param sourceRoot The root object to take a snapshot from.
     */
    public FileSystemDiff computeDiff(Root sourceRoot, FileSystemState oldSystemState, List<String> excludeGlobPatterns) throws IOException {
        ZonedDateTime start = ZonedDateTime.now();
        logTaskStart(Level.INFO, "Computing file differences", start, "at", sourceRoot.pathToRootDir());

        DetectChangeVisitor detectChangeVisitor = new DetectChangeVisitor(
                sourceRoot,
                oldSystemState,
                fileSystemAccessor,
                excludeGlobPatterns,
                this::logFileVisitingError,
                msg -> log(Level.DEBUG, msg)
        );
        fileSystemAccessor.visitFiles(sourceRoot.pathToRootDir(), detectChangeVisitor);
        FileSystemDiff fileSystemDiff = detectChangeVisitor.collectDiffResults();

        log(Level.INFO, fileSystemDiff.statistics().toString());
        logTaskEnd(Level.INFO, "Done computing file differences", Duration.between(start, ZonedDateTime.now()));
        return fileSystemDiff;
    }

    private void logFileVisitingError(Path erroneousPath, IOException exception) {
        log(Level.ERROR, "Could not visit %s: %s".formatted(erroneousPath, exception));
        logStacktrace(Level.DEBUG, exception);
    }

    /**
     * Not thread safe, not reusable.
     */
    private static class DetectChangeVisitor extends SimpleFileVisitor<Path> {

        private enum FileChangeState {UNCHANGED, CHANGED, NEW}

        private final AtomicInteger newCount = new AtomicInteger();
        private final AtomicInteger changedCount = new AtomicInteger();
        private final AtomicInteger unchangedCount = new AtomicInteger();
        private final AtomicInteger ignoredCount = new AtomicInteger();
        private final AtomicInteger errorCount = new AtomicInteger();

        private final Root sourceRoot;
        private final FileSystemState oldSystemState;
        private final FileSystemAccessor fileSystemAccessor;
        private final BiConsumer<Path, IOException> exceptionHandler;
        private final Consumer<String> messageHandler;
        private final List<PathMatcher> ignorePathMatchers;

        private final Set<Path> processedNewFiles = new HashSet<>();
        private final FileSystemNode systemDiffTree = FileSystemNode.getNew();

        public DetectChangeVisitor(
                Root sourceRoot,
                FileSystemState oldSystemState,
                FileSystemAccessor fileSystemAccessor,
                List<String> ignoreGlobPatterns,
                BiConsumer<Path, IOException> exceptionHandler, Consumer<String> messageHandler) {
            this.sourceRoot = sourceRoot;
            this.oldSystemState = oldSystemState;
            this.fileSystemAccessor = fileSystemAccessor;
            this.exceptionHandler = exceptionHandler;
            this.messageHandler = messageHandler;
            ignorePathMatchers = ignoreGlobPatterns.stream().map(FileSystemAccessor::getGlobPathMatcher).toList();
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            // compute relative dir as we do not want to exclude on components includes in the source root
            Path relDir = sourceRoot.rootDirLocation().relativize(dir);
            if (isExcluded(relDir)) {
                ignoredCount.getAndIncrement();
                messageHandler.accept("IGNORED (including subtree): " + relDir);
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            Path currentNewPath = sourceRoot.rootDirLocation().relativize(file);

            if (isExcluded(currentNewPath)) {
                ignoredCount.getAndIncrement();
                messageHandler.accept("IGNORED: " + currentNewPath);
            } else {
                FileSystemNode newNode = systemDiffTree.insert(currentNewPath);
                try {
                    switch (determineChange(oldSystemState, sourceRoot.rootDirLocation(), currentNewPath, attrs)) {
                        case UNCHANGED -> unchangedCount.getAndIncrement();
                        case CHANGED -> {
                            changedCount.getAndIncrement();
                            newNode.markAsChanged();
                        }
                        case NEW -> {
                            newCount.getAndIncrement();
                            newNode.markAsChanged();
                        }
                    }
                } catch (IOException e) {
                    errorCount.getAndIncrement();
                    newNode.markAsChanged();
                    exceptionHandler.accept(file, e);
                }
            }
            processedNewFiles.add(currentNewPath);
            return FileVisitResult.CONTINUE;
        }

        private boolean isExcluded(Path path) {
            return ignorePathMatchers.stream().anyMatch(matcher -> matcher.matches(path));
        }

        private FileChangeState determineChange(
                FileSystemState oldSystemState,
                Path root,
                Path newRelFilePath,
                BasicFileAttributes attrs
        ) throws IOException {
            Path newAbsFilePath = root.resolve(newRelFilePath);
            Instant newLastModified = attrs.lastModifiedTime().toInstant();
            Optional<FileState> lastCapturedState = oldSystemState.get(newRelFilePath);
            if (lastCapturedState.isPresent()) {
                FileState oldFileState = lastCapturedState.get();
                if (newLastModified.isAfter(oldFileState.getLastModified())) {
                    boolean hasChecksumChanged;
                    try {
                        hasChecksumChanged = !fileSystemAccessor.areChecksumsEqual(oldFileState.getChecksum(), newAbsFilePath);
                    } catch (IOException e) {
                        String errorMsg = "Could not determine hash at %s: %s".formatted(newAbsFilePath, e.getMessage());
                        throw new IOException(errorMsg, e);
                    }
                    if (hasChecksumChanged) {
                        messageHandler.accept(FileChangeState.CHANGED + ": " + newAbsFilePath);
                        return FileChangeState.CHANGED;
                    }
                }
                // we assume that newModified less or equal oldModified indicates an unchanged file
            } else {
                messageHandler.accept(FileChangeState.NEW + ": " + newAbsFilePath);
                return FileChangeState.NEW;
            }
            messageHandler.accept(FileChangeState.UNCHANGED + ": " + newAbsFilePath);
            return FileChangeState.UNCHANGED;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            exceptionHandler.accept(file, exc);
            return FileVisitResult.CONTINUE;
        }

        public FileSystemDiff collectDiffResults() {
            // determine no longer present files and mark former containing directories as changed
            FileSystemState oldStatesOfNotDeletedFiles = oldSystemState.newBySetUnion(processedNewFiles);
            FileSystemState removedStates = oldSystemState.newBySetMinus(oldStatesOfNotDeletedFiles);
            for (Path noLongerPresentPath : removedStates.paths()) {
                systemDiffTree.getDeepestKnownAlong(noLongerPresentPath).markAsChanged();
            }

            return new FileSystemDiff(
                    sourceRoot,
                    oldStatesOfNotDeletedFiles,
                    systemDiffTree,
                    new FileSystemDiff.Statistics(
                            newCount.get(),
                            removedStates.fileCount(),
                            changedCount.get(),
                            unchangedCount.get(),
                            ignoredCount.get(),
                            errorCount.get()));
        }


    }

}

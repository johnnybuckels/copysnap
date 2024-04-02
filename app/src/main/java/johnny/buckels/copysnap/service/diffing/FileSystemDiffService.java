package johnny.buckels.copysnap.service.diffing;

import johnny.buckels.copysnap.model.FileState;
import johnny.buckels.copysnap.model.FileSystemState;
import johnny.buckels.copysnap.service.logging.AbstractMessageProducer;
import johnny.buckels.copysnap.service.logging.Message;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FileSystemDiffService extends AbstractMessageProducer {

    private final FileSystemAccessor fileSystemAccessor;

    public FileSystemDiffService(FileSystemAccessor fileSystemAccessor) {
        this.fileSystemAccessor = fileSystemAccessor;
    }

    private enum FileChangeState {UNCHANGED, CHANGED, NEW, ERROR}

    /**
     * This method accesses the file system.
     * @param root The absolute path where the relative paths reside in. Used to access actual files on disk.
     * @param newRelativePaths List of relative file paths
     */
    public FileSystemDiff computeDiff(Path root, List<Path> newRelativePaths, FileSystemState oldSystemState) {
        if (!root.isAbsolute())
            throw new IllegalArgumentException("Can not process non-absolute root path: " + root);
        FileSystemNode systemDiffTree = FileSystemNode.getNew();
        int newCount = 0;
        int changedCount = 0;
        int unchangedCount = 0;
        int errorCount = 0;

        // determine changed existing changed files
        for (Path currentNewPath : newRelativePaths) {
            FileSystemNode newNode = systemDiffTree.insert(currentNewPath);
            switch (determineChange(oldSystemState, root, currentNewPath)) {
                case UNCHANGED -> unchangedCount++;
                case CHANGED -> {
                    changedCount++;
                    newNode.markAsChanged();
                }
                case NEW -> {
                    newCount++;
                    newNode.markAsChanged();
                }
                case ERROR -> {
                    errorCount++;
                    newNode.markAsChanged();
                }
            }
        }
        // determine no longer present files and mark former containing directories as changed
        Map<Path, FileState> noLongerPresentStates = oldSystemState.computeMissingStates(newRelativePaths);
        for (FileState fileState : noLongerPresentStates.values()) {
            systemDiffTree.getDeepestKnownAlong(fileState.getPath()).markAsChanged();
        }

        int removedCount = noLongerPresentStates.size();
        messageConsumer.consumeMessage(Message.info("New: %s, Changed: %s, Removed: %s, Unchanged: %s, Errors: %s",
                newCount, changedCount, removedCount, unchangedCount, errorCount));
        return new FileSystemDiff(root, oldSystemState.getRootPath(), systemDiffTree, new FileSystemDiff.DiffCounts(newCount, removedCount, changedCount, unchangedCount, errorCount));
    }

    private FileChangeState determineChange(FileSystemState oldSystemState, Path root, Path newRelFilePath) {
        Path newAbsFilePath = root.resolve(newRelFilePath);
        Instant newLastModified;
        try {
            newLastModified = fileSystemAccessor.getLastModifiedTime(newAbsFilePath);
        } catch (IOException e) {
            messageConsumer.consumeMessage(Message.error("Could not determine last modified time at %s: %s", newAbsFilePath, e.getMessage()), e);
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
                    messageConsumer.consumeMessage(Message.error("Could not determine hash at %s: %s", newAbsFilePath, e.getMessage()), e);
                    return FileChangeState.ERROR;
                }
                if (hasChecksumChanged) {
                    return FileChangeState.CHANGED;
                }
            }
            // we assume that newModified less or equal oldModified indicates an unchanged file
        } else {
            return FileChangeState.NEW;
        }
        return FileChangeState.UNCHANGED;
    }

}

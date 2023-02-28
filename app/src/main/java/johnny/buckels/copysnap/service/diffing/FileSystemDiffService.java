package johnny.buckels.copysnap.service.diffing;

import johnny.buckels.copysnap.model.FileState;
import johnny.buckels.copysnap.model.FileSystemState;
import johnny.buckels.copysnap.service.logging.AbstractMessageProducer;
import johnny.buckels.copysnap.service.logging.Message;

import java.util.HashSet;
import java.util.Set;

public class FileSystemDiffService extends AbstractMessageProducer {

    private final FileSystemState newState;
    private final FileSystemState oldState;

    public FileSystemDiffService(FileSystemState newState, FileSystemState oldState) {
        this.newState = newState;
        this.oldState = oldState;
    }

    public FileSystemDiff computeDiff() {
        messageConsumer.consumeMessage(Message.info("Determining file differences."));
        FileSystemNode systemDiffTree = FileSystemNode.getNew();
        int newOrChanged = 0;
        Set<FileState> remainingOldStates = new HashSet<>(oldState.getStates());
        // determine changed existing changed files
        for (FileState fileState : newState.getStates()) {
            FileSystemNode newNode = systemDiffTree.insert(fileState.getPath());
            if (!remainingOldStates.remove(fileState)) {
                newNode.markAsChanged();
                newOrChanged++;
            }
        }
        // determine no longer present files
        int movedDeletedCount = remainingOldStates.size();
        for (FileState fileState : remainingOldStates) {
            systemDiffTree.getDeepestKnownAlong(fileState.getPath()).markAsChanged();
        }

        messageConsumer.consumeMessage(Message.info("New/Changed: %s, Moved/Deleted: %s",
                newOrChanged, movedDeletedCount));
        return new FileSystemDiff(newState.getRootPath(), oldState.getRootPath(), systemDiffTree);
    }

}

package johnny.buckels.copysnap.service.diffing;

import johnny.buckels.copysnap.model.FileState;
import johnny.buckels.copysnap.model.FileSystemState;
import johnny.buckels.copysnap.service.logging.AbstractMessageProducer;
import johnny.buckels.copysnap.service.logging.Message;

public class FileSystemDiffService extends AbstractMessageProducer {

    private final FileSystemState newState;
    private final FileSystemState oldState;

    public FileSystemDiffService(FileSystemState newState, FileSystemState oldState) {
        this.newState = newState;
        this.oldState = oldState;
    }

    public FileSystemDiff computeDiff() {
        messageConsumer.consumeMessage(Message.info("Determining file differences."));
        FileSystemNode systemDiffTree = FileSystemNode.createNew();
        int changedCount = 0;
        for (FileState fileState : newState.getStates()) {
            FileSystemNode newNode = systemDiffTree.insert(fileState.getPath());
            if (!oldState.contains(fileState)) {
                newNode.markAsChanged();
                changedCount++;
            }
        }
        messageConsumer.consumeMessage(Message.info("Changed files: " + changedCount));
        return new FileSystemDiff(newState.getRootPath(), oldState.getRootPath(), systemDiffTree);
    }

}

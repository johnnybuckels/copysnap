package johnny.buckels.copysnap.service;

import johnny.buckels.copysnap.model.FileSystemState;
import johnny.buckels.copysnap.service.diffing.FileSystemDiff;
import johnny.buckels.copysnap.service.diffing.FileSystemDiffService;
import johnny.buckels.copysnap.service.diffing.copy.CopyAction;
import johnny.buckels.copysnap.service.logging.AbstractMessageProducer;
import johnny.buckels.copysnap.service.logging.Message;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public class SnapshotService extends AbstractMessageProducer {

    private final FileSystemState oldState;
    private final FileSystemState newState;

    /**
     * If '/a/b/c/r' is the path to the file system 'r' to take a snapshot from, then sourceRoot is the path "/a/b/c"
     * and sourceFileSystemName is the String "r".
     *
     * @param newState the current state of the file system to take a snapshot from.
     * @param oldState the old state to compare to.
     */
    public SnapshotService(FileSystemState newState, FileSystemState oldState) {
        this.newState = newState;
        this.oldState = oldState;
    }

    /**
     * Creates a snapshot of the source directory and writes files to the specified destination;
     */
    public void createNewSnapshot(Path destination) {
        messageConsumer.consumeMessage(Message.info("Creating new Snapshot at " + destination));

        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(newState, oldState);
        fileSystemDiffService.setMessageConsumer(messageConsumer);
        FileSystemDiff fileSystemDiff = fileSystemDiffService.computeDiff();

        Set<CopyAction> copyActions = fileSystemDiff.computeCopyActions(destination);
        messageConsumer.consumeMessage(Message.info("Creating files."));
        int performedCount = 0;
        for (CopyAction copyAction : copyActions) {
            try {
                copyAction.perform();
                performedCount++;
            } catch (IOException e) {
                messageConsumer.consumeMessage(Message.error("Could not perform copy action " + copyAction + ": " + e));
            }
            messageConsumer.consumeMessageOverride(Message.progressInfo(performedCount, copyActions.size()));
        }
        messageConsumer.newLine();
    }

}

package johnny.buckels.copysnap.service;

import johnny.buckels.copysnap.model.FileSystemState;
import johnny.buckels.copysnap.service.diffing.FileSystemAccessor;
import johnny.buckels.copysnap.service.diffing.FileSystemDiff;
import johnny.buckels.copysnap.service.diffing.FileSystemDiffService;
import johnny.buckels.copysnap.service.diffing.copy.CopyAction;
import johnny.buckels.copysnap.service.logging.AbstractMessageProducer;
import johnny.buckels.copysnap.service.logging.Message;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class SnapshotService extends AbstractMessageProducer {

    private final Path root;
    private final List<Path> newPaths;
    private final FileSystemState oldState;

    public SnapshotService(Path root, List<Path> newPaths, FileSystemState oldState) {
        this.root = root;
        this.newPaths = newPaths;
        this.oldState = oldState;
    }

    /**
     * Creates a snapshot of the source directory and writes files to the specified destination;
     * @return The file system state of the newly created snapshot
     */
    public FileSystemState createNewSnapshot(Path destination) {
        messageConsumer.consumeMessage(Message.info("Creating new snapshot at " + destination));

        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(FileSystemAccessor.newDefaultAccessor());
        fileSystemDiffService.setMessageConsumer(messageConsumer);
        FileSystemDiff fileSystemDiff = fileSystemDiffService.computeDiff(root, newPaths, oldState);

        Set<CopyAction> copyActions = fileSystemDiff.computeCopyActions(destination);
        int performedCount = 0;
        FileSystemState.Builder newStateBuilder = FileSystemState.builder(destination, oldState);
        for (CopyAction copyAction : copyActions) {
            try {
                copyAction.perform()
                        .ifPresent(newStateBuilder::add);
            } catch (IOException e) {
                messageConsumer.consumeMessage(Message.error("Could not perform copy action " + copyAction + ": " + e));
            }
            messageConsumer.consumeMessageOverride(Message.progressInfo("Writing files", performedCount, copyActions.size()));
            performedCount++;
        }
        messageConsumer.newLine();
        return newStateBuilder.build();
    }

}

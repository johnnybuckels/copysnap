package johnny.buckels.copysnap.service;

import johnny.buckels.copysnap.model.FileSystemState;
import johnny.buckels.copysnap.model.Root;
import johnny.buckels.copysnap.service.diffing.FileSystemAccessor;
import johnny.buckels.copysnap.service.diffing.FileSystemDiff;
import johnny.buckels.copysnap.service.diffing.FileSystemDiffService;
import johnny.buckels.copysnap.service.diffing.copy.CopyAction;
import johnny.buckels.copysnap.service.logging.AbstractMessageProducer;
import johnny.buckels.copysnap.service.logging.Message;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class SnapshotService extends AbstractMessageProducer {

    private final Root sourceRoot;
    private final FileSystemState oldState;

    public SnapshotService(Root sourceRoot,FileSystemState oldState) {
        this.sourceRoot = sourceRoot;
        this.oldState = oldState;
    }

    /**
     * Creates a snapshot of the source directory and writes files to the specified destination;
     * @return The file system state of the newly created snapshot
     */
    public FileSystemState createNewSnapshot(Path destination) {
        messageConsumer.consumeMessage(Message.info("Creating new snapshot at " + destination));
        List<Path> newPaths = gatherNewPaths();
        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(FileSystemAccessor.newDefaultAccessor());
        fileSystemDiffService.setMessageConsumer(messageConsumer);
        FileSystemDiff fileSystemDiff = fileSystemDiffService.computeDiff(sourceRoot.rootDirLocation(), newPaths, oldState);

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

    private List<Path> gatherNewPaths() {
        try (Stream<Path> files = Files.walk(sourceRoot.pathToRootDir(), FileVisitOption.FOLLOW_LINKS)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(p -> sourceRoot.rootDirLocation().relativize(p))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not iterate over directory contents at " + sourceRoot + ": " + e.getMessage(), e);
        }
    }

}

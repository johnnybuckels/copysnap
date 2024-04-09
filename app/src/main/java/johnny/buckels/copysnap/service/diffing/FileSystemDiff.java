package johnny.buckels.copysnap.service.diffing;

import johnny.buckels.copysnap.model.FileSystemState;
import johnny.buckels.copysnap.model.Root;
import johnny.buckels.copysnap.service.diffing.copy.CopyAction;
import johnny.buckels.copysnap.service.diffing.copy.PlainCopyAction;
import johnny.buckels.copysnap.service.diffing.copy.SymbolicLinkCopyAction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 *                                 FileSystem                        CopySnapCopies
 * newSystemRootLocation    ---->  ...                              ...
 * newSystemRoot            ---->      |- Root                          |- 2024-02-23    <---- oldSystemRootLocation
 *                                        |-...                            |- Root       <---- oldSystemRoot
 *                                        |-...                                |-...
 *                                                                             |-...
 *                                                                     |- 2024-04-04     <---- destination
 *                                                                         |- (about to copy here...)
 * newSystemRootLocation: The directory where the root path of the current filesystem is located in.
 * oldSystemRootLocation: The directory where the root path of the last filesystem-snapshot is located in.
 * destination: The directory where the copy of the filesystem should reside in.
 */
public record FileSystemDiff(
        Root sourceRoot,
        FileSystemState remainingOldStates,
        FileSystemNode diffTree,
        DiffCounts counts
) {
    /**
     * @param destination the directory where the copy of the filesystem should reside in.
     */
    public Actions computeCopyActions(Path destination) {
        Set<CopyAction> copyActions = new HashSet<>();
        for (FileSystemNode file : diffTree.getLeafs()) {
            if (file.isChanged()) {
                copyActions.add(new PlainCopyAction(sourceRoot.rootDirLocation(), destination, file.getPath()));
            } else {
                Path upperMostUnchanged = file.getUppermostUnchanged().getPath();
                copyActions.add(new SymbolicLinkCopyAction(remainingOldStates.info().rootLocation(), destination, upperMostUnchanged));
            }
        }
        return new Actions(copyActions);
    }

    record DiffCounts(int newCount, int removedCount, int changedCount, int unchangedCount, int errorCount) {}

    public class Actions {

        private final Set<CopyAction> copyActions;

        private Actions(Set<CopyAction> copyActions) {
            this.copyActions = copyActions;
        }

        // TODO: logging
        /**
         *
         * @return The new file system state.
         */
        public FileSystemState apply(FileSystemAccessor fsa) {
//        int performedCount = 0;
            FileSystemState.Builder newStateBuilder = FileSystemState.builder(remainingOldStates);
            for (CopyAction copyAction : copyActions) {
                try {
                    copyAction.perform(fsa)
                            .ifPresent(newStateBuilder::add);
                } catch (IOException e) {
//                consumeMessage(Message.error("Could not perform copy action " + copyAction + ": " + e));
                }
//            messageConsumer.consumeMessageOverride(Message.progressInfo("Writing files", performedCount, copyActions.size()));
//            performedCount++;
            }
//        messageConsumer.newLine();
            return newStateBuilder.build();
        }

        public Set<CopyAction> getActions() {
            return new HashSet<>(copyActions);
        }
    }


}

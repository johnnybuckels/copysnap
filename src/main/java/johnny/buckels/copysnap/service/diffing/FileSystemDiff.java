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


public record FileSystemDiff(
        Root sourceRoot,
        FileSystemState stillExistingFiles,
        FileSystemNode diffTree,
        DiffCounts counts
) {

    /**
     * @param destination the directory where the copy of the filesystem should reside in.
     */
    public Actions computeCopyActions(Path destination, Path oldRootLocation) {
        Set<CopyAction> copyActions = new HashSet<>();
        for (FileSystemNode file : diffTree.getLeafs()) {
            if (file.isChanged()) {
                copyActions.add(new PlainCopyAction(sourceRoot.rootDirLocation(), destination, file.getPath()));
            } else {
                Path upperMostUnchanged = file.getUppermostUnchanged().getPath();
                copyActions.add(new SymbolicLinkCopyAction(oldRootLocation, destination, upperMostUnchanged));
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
         * @return The new file system state.
         */
        public FileSystemState apply(FileSystemAccessor fsa) {
//        int performedCount = 0;
            FileSystemState.Builder newStateBuilder = FileSystemState.builder(stillExistingFiles);
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

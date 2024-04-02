package johnny.buckels.copysnap.service.diffing;

import johnny.buckels.copysnap.service.diffing.copy.CopyAction;
import johnny.buckels.copysnap.service.diffing.copy.PlainCopyAction;
import johnny.buckels.copysnap.service.diffing.copy.SymbolicLinkCopyAction;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 *                      FileSystem                        CopySnapCopies
 *                       ...                              ...
 * newSystemRoot ---->      |- Root                          |- 2024-02-23
 *                              |-...                            |- Root       <---- oldSystemRoot
 *                              |-...                                |-...
 *                                                                   |-...
 *                                                           |- 2024-04-04     <---- destination
 *                                                               |- (about to copy here...)
 * newSystemRoot: The directory where the root path of the current filesystem is located in.
 * oldSystemRoot: The directory where the root path of the last filesystem-snapshot is located in.
 * destination: The directory where the copy of the filesystem should reside in.
 */
public class FileSystemDiff {

    /**
     * The directory where the root path of the current filesystem is located in.
     */
    private final Path newSystemRoot;
    /**
     * The directory where the root path of the last filesystem-snapshot is located in.
     */
    private final Path oldSystemRoot;
    private final FileSystemNode diffTree;
    private final DiffCounts counts;

    FileSystemDiff(Path newSystemRoot, Path oldSystemRoot, FileSystemNode diffTree, DiffCounts counts) {
        this.newSystemRoot = newSystemRoot;
        this.oldSystemRoot = oldSystemRoot;
        this.diffTree = diffTree;
        this.counts = counts;
    }

    /**
     * @param destination the directory where the copy of the filesystem should reside in.
     */
    public Set<CopyAction> computeCopyActions(Path destination) {
        Set<CopyAction> copyActions = new HashSet<>();
        for (FileSystemNode file : diffTree.getLeafs()) {
            if (file.isChanged()) {
                copyActions.add(new PlainCopyAction(newSystemRoot, destination, file.getPath()));
            } else {
                Path upperMostUnchanged = file.getUppermostUnchanged().getPath();
                copyActions.add(new SymbolicLinkCopyAction(oldSystemRoot, destination, upperMostUnchanged));
            }
        }
        return copyActions;
    }

    public DiffCounts getCounts() {
        return counts;
    }

    public record DiffCounts(int newCount, int removedCount, int changedCount, int unchangedCount, int errorCount) {}

}

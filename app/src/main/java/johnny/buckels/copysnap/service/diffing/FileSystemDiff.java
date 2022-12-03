package johnny.buckels.copysnap.service.diffing;

import johnny.buckels.copysnap.service.diffing.copy.CopyAction;
import johnny.buckels.copysnap.service.diffing.copy.PlainCopyAction;
import johnny.buckels.copysnap.service.diffing.copy.SymbolicLinkCopyAction;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

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

    public FileSystemDiff(Path newSystemRoot, Path oldSystemRoot, FileSystemNode diffTree) {
        this.newSystemRoot = newSystemRoot;
        this.oldSystemRoot = oldSystemRoot;
        this.diffTree = diffTree;
    }

    /**
     * @param destination the directory where the copy of the filesystem should reside in.
     */
    public Set<CopyAction> computeCopyActions(Path destination) {
        Set<CopyAction> copyActions = new HashSet<>();
        for (FileSystemNode file : diffTree.getLeafs()) {
            if (file.isChanged()) {
                copyActions.add(new PlainCopyAction(newSystemRoot.resolve(file.getValue()), destination.resolve(file.getValue())));
            } else {
                Path upperMostUnchanged = file.getUppermostUnchanged().getValue();
                copyActions.add(new SymbolicLinkCopyAction(oldSystemRoot.resolve(upperMostUnchanged), destination.resolve(upperMostUnchanged)));
            }
        }
        return copyActions;
    }

}

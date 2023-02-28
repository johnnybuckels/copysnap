package johnny.buckels.copysnap.service.diffing.copy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SymbolicLinkCopyAction extends AbstractCopyAction {

    public SymbolicLinkCopyAction(Path symlinkTarget, Path symlinkLocation) {
        super(symlinkTarget, symlinkLocation);
    }

    @Override
    public void perform() throws IOException {
        createParentDirs(destinationToCopyTo);
        Files.createSymbolicLink(destinationToCopyTo, sourceToCopy);
    }

    @Override
    public String toString() {
        return "SymbolicLinkCopyAction{" +
                "symlinkTarget=" + sourceToCopy +
                ", symlinkLocation=" + destinationToCopyTo +
                '}';
    }

}

package johnny.buckels.copysnap.service.diffing.copy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PlainCopyAction extends AbstractCopyAction {

    public PlainCopyAction(Path from, Path to) {
        super(from, to);
    }

    @Override
    public void perform() throws IOException {
        createParentDirs(destinationToCopyTo);
        Files.copy(sourceToCopy, destinationToCopyTo);
    }

    @Override
    public String toString() {
        return "PlainCopyAction{" +
                "sourceToCopy=" + sourceToCopy +
                ", destinationToCopyTo=" + destinationToCopyTo +
                '}';
    }

}

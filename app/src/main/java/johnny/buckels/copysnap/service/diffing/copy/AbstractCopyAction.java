package johnny.buckels.copysnap.service.diffing.copy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public abstract class AbstractCopyAction implements CopyAction {

    final Path sourceToCopy;
    final Path destinationToCopyTo;

    protected AbstractCopyAction(Path sourceToCopy, Path destinationToCopyTo) {
        this.sourceToCopy = sourceToCopy;
        this.destinationToCopyTo = destinationToCopyTo;
    }

    void createParentDirs(Path p) throws IOException {
        Path parent = p.getParent();
        if (parent != null)
            Files.createDirectories(parent);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractCopyAction that = (AbstractCopyAction) o;
        return sourceToCopy.equals(that.sourceToCopy) && destinationToCopyTo.equals(that.destinationToCopyTo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceToCopy, destinationToCopyTo);
    }

}

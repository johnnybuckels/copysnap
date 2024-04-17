package johnny.buckels.copysnap.service.diffing.copy;

import johnny.buckels.copysnap.service.diffing.FileSystemAccessor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

abstract class AbstractCopyAction implements CopyAction {

    final Path sourceRoot;
    final Path destinationRoot;
    final Path relPath;

    protected AbstractCopyAction(Path sourceRoot, Path destinationRoot, Path relPath) {
        this.sourceRoot = sourceRoot;
        this.destinationRoot = destinationRoot;
        this.relPath = relPath;
    }

    void createParentDirs(Path p, FileSystemAccessor fsa) throws IOException {
        Path parent = p.getParent();
        if (parent != null)
            fsa.createDirectories(parent);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractCopyAction that = (AbstractCopyAction) o;
        return Objects.equals(sourceRoot, that.sourceRoot) && Objects.equals(destinationRoot, that.destinationRoot) && Objects.equals(relPath, that.relPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceRoot, destinationRoot, relPath);
    }

    @Override
    public String toString() {
        return "%s{sourceRoot=%s, destinationRoot=%s, relPath=%s}"
                .formatted(this.getClass().getSimpleName(), sourceRoot, destinationRoot, relPath);
    }
}

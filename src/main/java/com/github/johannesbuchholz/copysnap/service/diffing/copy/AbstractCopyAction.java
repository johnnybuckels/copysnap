package com.github.johannesbuchholz.copysnap.service.diffing.copy;

import com.github.johannesbuchholz.copysnap.service.diffing.FileSystemAccessor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

abstract class AbstractCopyAction implements CopyAction {

    final Path sourceRootLocation;
    final Path destinationRootLocation;
    final Path relPath;

    protected AbstractCopyAction(Path sourceRootLocation, Path destinationRootLocation, Path relPath) {
        this.sourceRootLocation = Objects.requireNonNull(sourceRootLocation);
        this.destinationRootLocation = Objects.requireNonNull(destinationRootLocation);
        this.relPath = Objects.requireNonNull(relPath);
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
        return Objects.equals(sourceRootLocation, that.sourceRootLocation) && Objects.equals(destinationRootLocation, that.destinationRootLocation) && Objects.equals(relPath, that.relPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceRootLocation, destinationRootLocation, relPath);
    }

    @Override
    public String toString() {
        return "%s{sourceRoot=%s, destinationRoot=%s, relPath=%s}"
                .formatted(this.getClass().getSimpleName(), sourceRootLocation, destinationRootLocation, relPath);
    }
}

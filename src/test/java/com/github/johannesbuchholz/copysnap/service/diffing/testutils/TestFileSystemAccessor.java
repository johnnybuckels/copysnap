package com.github.johannesbuchholz.copysnap.service.diffing.testutils;

import com.github.johannesbuchholz.copysnap.model.state.CheckpointChecksum;
import com.github.johannesbuchholz.copysnap.service.diffing.FileSystemAccessor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record TestFileSystemAccessor(
        Map<Path, Instant> lastModified,
        Map<Path, CheckpointChecksum> checksums,
        Map<Path, List<Path>> pathsByRootDir,
        Map<Path, byte[]> contentByPath,
        Map<Path, OutputStream> dataSinksByPath
) implements FileSystemAccessor {

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Instant getLastModifiedTime(Path p) {
        if (lastModified.isEmpty()) {
            // if no map is given, return any Instant.
            return Instant.now();
        }
        return Objects.requireNonNull(lastModified.get(p), p.toString());
    }

    @Override
    public boolean areChecksumsEqual(CheckpointChecksum expectedChecksum, Path p) {
        return Optional.ofNullable(checksums.get(p)).map(expectedChecksum::equals).orElseThrow();
    }

    @Override
    public OutputStream createNewOutputStream(Path path) {
        return Objects.requireNonNull(dataSinksByPath.get(path));
    }

    @Override
    public InputStream createNewInputStream(Path path) {
        return new ByteArrayInputStream(Objects.requireNonNull(contentByPath.get(path), path.toString()));
    }

    @Override
    public void createDirectories(Path path) {
        // do nothing
    }

    @Override
    public void visitFiles(Path root, FileVisitor<Path> visitor) throws IOException {
        for (Path p : pathsByRootDir.get(root)) {
            Instant lmt = getLastModifiedTime(p);
            visitor.visitFile(p, new DummyFileAttribute(lmt));
        }
    }

    @Override
    public void createSymbolicLink(Path absDestination, Path absSource) {
        // do nothing
    }

    record DummyFileAttribute(Instant lmt) implements BasicFileAttributes {

        @Override
        public FileTime lastModifiedTime() {
            return FileTime.from(lmt);
        }

        @Override
        public FileTime lastAccessTime() {
            return null;
        }

        @Override
        public FileTime creationTime() {
            return null;
        }

        @Override
        public boolean isRegularFile() {
            return false;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public boolean isSymbolicLink() {
            return false;
        }

        @Override
        public boolean isOther() {
            return false;
        }

        @Override
        public long size() {
            return 0;
        }

        @Override
        public Object fileKey() {
            return null;
        }
    }

    public static class Builder {

        private Map<Path, Instant> lastModified = Map.of();
        private Map<Path, CheckpointChecksum> checksums = Map.of();
        private Map<Path, List<Path>> pathsByRootDir = Map.of();
        private Map<Path, byte[]> contentByPath = Map.of();
        private Map<Path, OutputStream> dataSinksByPath = Map.of();

        private Builder() {
            // use factory
        }

        public Builder setLastModified(Map<Path, Instant> lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        public Builder setChecksums(Map<Path, CheckpointChecksum> checksums) {
            this.checksums = checksums;
            return this;
        }

        public Builder setPathsByRootDir(Map<Path, List<Path>> pathsByRootDir) {
            this.pathsByRootDir = pathsByRootDir;
            return this;
        }

        public Builder setContentByPath(Map<Path, byte[]> contentByPath) {
            this.contentByPath = contentByPath;
            return this;
        }

        public Builder setDataSinksByPath(Map<Path, OutputStream> dataSinksByPath) {
            this.dataSinksByPath = dataSinksByPath;
            return this;
        }

        public TestFileSystemAccessor build() {
            return new TestFileSystemAccessor(lastModified, checksums, pathsByRootDir, contentByPath, dataSinksByPath);
        }
    }
}

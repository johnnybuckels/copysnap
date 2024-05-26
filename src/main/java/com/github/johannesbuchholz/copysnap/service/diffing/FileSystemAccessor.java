package com.github.johannesbuchholz.copysnap.service.diffing;

import com.github.johannesbuchholz.copysnap.model.CheckpointChecksum;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.stream.Stream;

public interface FileSystemAccessor {

    static FileSystemAccessor newDefaultAccessor() {
        return new DefaultFileSystemAccessor();
    }

    Instant getLastModifiedTime(Path p) throws IOException;

    boolean areChecksumsEqual(CheckpointChecksum expectedChecksum, Path p) throws IOException;

    OutputStream createNewOutputStream(Path path) throws IOException;

    InputStream createNewInputStream(Path path) throws IOException;

    void createDirectories(Path path) throws IOException;

    Stream<Path> findFiles(Path path) throws IOException;

    void createSymbolicLink(Path absDestination, Path absSource) throws IOException;

    class DefaultFileSystemAccessor implements FileSystemAccessor {

        @Override
        public Instant getLastModifiedTime(Path p) throws IOException {
            return Files.getLastModifiedTime(p).toInstant();
        }

        @Override
        public boolean areChecksumsEqual(CheckpointChecksum expectedChecksum, Path p) throws IOException {
            try (InputStream is = Files.newInputStream(p)) {
                return expectedChecksum.hasSameChecksum(is);
            }
        }

        @Override
        public OutputStream createNewOutputStream(Path path) throws IOException {
            return Files.newOutputStream(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        }

        @Override
        public InputStream createNewInputStream(Path path) throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public void createDirectories(Path path) throws IOException {
            Files.createDirectories(path);
        }

        @Override
        public Stream<Path> findFiles(Path path) throws IOException {
            return Files.find(path, Integer.MAX_VALUE, (p, bfa) -> bfa.isRegularFile(), FileVisitOption.FOLLOW_LINKS);
        }

        @Override
        public void createSymbolicLink(Path absDestination, Path absSource) throws IOException {
            Files.createSymbolicLink(absDestination, absSource);
        }

    }

}

package com.github.johannesbuchholz.copysnap.service.diffing;

import com.github.johannesbuchholz.copysnap.model.state.CheckpointChecksum;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.Instant;
import java.util.Set;

public class DefaultFileSystemAccessor implements FileSystemAccessor {

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
    public void visitFiles(Path root, FileVisitor<Path> visitor) throws IOException {
        Files.walkFileTree(root, Set.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, visitor);
    }

    @Override
    public void createSymbolicLink(Path absDestination, Path absSource) throws IOException {
        Files.createSymbolicLink(absDestination, absSource);
    }

}

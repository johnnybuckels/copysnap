package com.github.johannesbuchholz.copysnap.service.diffing;

import com.github.johannesbuchholz.copysnap.model.state.CheckpointChecksum;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Instant;

public interface FileSystemAccessor {

    static FileSystemAccessor newDefaultAccessor() {
        return new DefaultFileSystemAccessor();
    }

    Instant getLastModifiedTime(Path p) throws IOException;

    boolean areChecksumsEqual(CheckpointChecksum expectedChecksum, Path p) throws IOException;

    OutputStream createNewOutputStream(Path path) throws IOException;

    InputStream createNewInputStream(Path path) throws IOException;

    void createDirectories(Path path) throws IOException;

    void visitFiles(Path root, FileVisitor<Path> visitor) throws IOException;

    void createSymbolicLink(Path absDestination, Path absSource) throws IOException;

    static PathMatcher getGlobPathMatcher(String globPattern) {
        return FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
    }

}

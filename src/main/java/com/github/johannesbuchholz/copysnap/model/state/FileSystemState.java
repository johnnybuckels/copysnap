package com.github.johannesbuchholz.copysnap.model.state;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

import static java.util.function.Predicate.not;

public class FileSystemState {

    public static FileSystemState empty() {
        return new FileSystemState(Map.of());
    }

    public static FileSystemState.Builder builder() {
        return new Builder();
    }

    public static FileSystemState.Builder builder(FileSystemState existingState) {
        return new Builder(existingState.statesByPath);
    }

    /**
     * Reads a file of the form
     * <p>
     * #ROOT_PATH
     * #DATE
     * #HASH & PATH
     * ...
     * #HASH & #PATH
     * </p>
     */
    public static FileSystemState read(InputStream is) throws IOException {
        FileSystemState.Builder builder = FileSystemState.builder();
        Optional<String> nextLineOpt;
        while ((nextLineOpt = readUntilNextNull(is)).isPresent()) {
            FileState fs = FileState.deserialize(nextLineOpt.get());
            builder.add(fs);
        }
        return builder.build();
    }

    private static Optional<String> readUntilNextNull(InputStream is) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(1024);
        int nextByte;
        while ((nextByte = is.read()) > -1) {
           if (nextByte == Character.MIN_VALUE) {
               // skip new line character
               long ignored = is.skip(1);
               ByteBuffer slice = bb.slice(0, bb.position());
               return Optional.of(StandardCharsets.UTF_8.decode(slice).toString());
           }
           if (bb.position() >= bb.capacity()) {
               ByteBuffer bbNew = ByteBuffer.allocate(bb.capacity() * 2);
               bb = bbNew.put(bb.array());
           }
           bb.put((byte) nextByte);
        }
        // the stream is exhausted. We do not return the buffer since no null byte has been found.
        return Optional.empty();
    }

    private final Map<Path, FileState> statesByPath;

    private FileSystemState(Map<Path, FileState> statesByPath) {
        this.statesByPath = statesByPath;
    }

    public Optional<FileState> get(Path relativePath) {
        return Optional.ofNullable(statesByPath.get(relativePath));
    }

    /**
     * @return A new state with all states from this that are contained in the specified paths.
     */
    public FileSystemState newBySetUnion(Set<Path> otherPaths) {
        Builder builder = FileSystemState.builder(this);
        statesByPath.keySet().stream()
                .filter(not(otherPaths::contains))
                .forEach(builder::remove);
        return builder.build();
    }

    /**
     * @return A new state with all states from this that are not contained in the specified state.
     */
    public FileSystemState newBySetMinus(FileSystemState other) {
        Builder builder = FileSystemState.builder(this);
        statesByPath.keySet().stream()
                .filter(other.statesByPath::containsKey)
                .forEach(builder::remove);
        return builder.build();
    }

    public int fileCount() {
        return statesByPath.size();
    }

    public void write(OutputStream os) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(os)) {
            for (FileState fileState : statesByPath.values()) {
                writeLine(writer, fileState.serialize());
            }
            writer.flush();
        }
    }

    /**
     * Each line is finished by {@link Character#MIN_VALUE}.
     * When parsing the file, line-wise reading could lead to errors since the path-strings may contain new-line
     * characters. To solve that issue, we decide to put "anchors" at the end of each line.
     * We also put new-line characters between each entry, to maintain human readability.
     */
    private void writeLine(Writer bw, String s) throws IOException {
        bw.write(s);
        bw.write(Character.MIN_VALUE);
        bw.write(System.lineSeparator());
    }

    public Set<Path> paths() {
        return Collections.unmodifiableSet(statesByPath.keySet());
    }

    /**
     * Not thread safe
     */
    public static class Builder {

        private final Map<Path, FileState> statesByPath;

        private Builder() {
            this(Map.of());
        }

        private Builder(Map<Path, FileState> statesByPath) {
            this.statesByPath = new HashMap<>(statesByPath);
        }

        public Builder add(FileState fileState) {
            if (fileState.getPath().isAbsolute())
                throw new IllegalArgumentException("Can not add absolute path: " + fileState.getPath());
            statesByPath.put(fileState.getPath(), fileState);
            return this;
        }

        public void remove(Path path) {
            statesByPath.remove(path);
        }

        public FileSystemState build() {
            return new FileSystemState(Collections.unmodifiableMap(statesByPath));
        }

    }

}

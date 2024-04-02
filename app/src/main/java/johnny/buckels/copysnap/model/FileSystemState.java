package johnny.buckels.copysnap.model;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FileSystemState {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    // TODO: Remove this field. Root path should only matter when callers actually want to access a file system.
    private final Path rootPath;
    private final ZonedDateTime created;
    /**
     * Contains relative FileStates w.r.t. {@link #rootPath}.
     * The absolute paths may be obtained by resolving these FileState's paths against {@link #rootPath}.
     */
    private final Map<Path, FileState> statesByPath;

    public static FileSystemState empty() {
        return new FileSystemState(null, ZonedDateTime.now(), Map.of());
    }

    public static FileSystemState.Builder builder(Path rootPath) {
        return new Builder(rootPath, empty());
    }

    public static FileSystemState.Builder builder(Path rootPath, FileSystemState existingState) {
        return new Builder(rootPath, existingState);
    }

    // TODO: Test serde
    /**
     * Reads a file of the form
     * <p>
     * #ROOT_PATH
     * #DATE
     * #HASH;#PATH
     * ...
     * #HASH;#PATH
     * </p>
     */
    public static FileSystemState read(Path path) {
        FileSystemState.Builder builder;
        try (InputStream is = Files.newInputStream(path)) {
            String rootPathLine = readUntilNextNull(is).orElseThrow(() -> new IllegalStateException("Could not read file system state from %s: Could not retrieve first line".formatted(path)));
            builder = FileSystemState.builder(Path.of(rootPathLine));
            // read 'created'-line which is currently unused
            readUntilNextNull(is).orElseThrow(() -> new IllegalStateException("Could not read file system state from %s: Could not retrieve second line".formatted(path)));
            
            Optional<String> nextLineOpt;
            while ((nextLineOpt = readUntilNextNull(is)).isPresent()) {
                FileState fs = FileState.deserialize(nextLineOpt.get());
                builder.add(fs);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read FileSystemState from %s: %s".formatted(path, e.getMessage()), e);
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

    private FileSystemState(Path rootPath, ZonedDateTime created, Map<Path, FileState> statesByPath) {
        this.rootPath = rootPath;
        this.created = created;
        this.statesByPath = statesByPath;
    }

    public Path getRootPath() {
        return rootPath;
    }

    public ZonedDateTime getCreated() {
        return created;
    }

    Set<FileState> getStates() {
        return new HashSet<>(statesByPath.values());
    }

    public Optional<FileState> get(Path relativePath) {
        return Optional.ofNullable(statesByPath.get(relativePath));
    }

    public Map<Path, FileState> computeMissingStates(Collection<Path> paths) {
        HashMap<Path, FileState> statesCopy = new HashMap<>(statesByPath);
        paths.forEach(statesCopy::remove);
        return statesCopy;
    }

    /**
     * Returns new object identical to this but with the specified root path.
     */
    public FileSystemState withRoot(Path newRoot) {
        return new FileSystemState(newRoot, created, statesByPath);
    }

    /**
     * Writes a file of the form
     * <p>
     * ROOT_PATH\NUL\n
     * date\NUL\n
     * checksum;modified;path\NUL\n
     * ...
     * checksum;modified;path\NUL\n
     * </p>
     */
    public void write(Path path) {
        try (BufferedWriter bw = Files.newBufferedWriter(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writeLine(bw, rootPath.toString());
            writeLine(bw, created.format(DATE_TIME_FORMATTER));
            for (FileState fileState : statesByPath.values()) {
                writeLine(bw, fileState.serialize());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write FileSystemState to %s: %s".formatted(path, e.getMessage()), e);
        }
    }

    /**
     * Each line is surrounded by {@link Byte#}.
     * When parsing the file, line-wise reading could lead to errors since the path-strings may contain new-line
     * characters. To solve that issue, we decide to put "anchors" around each entry.
     * We also put new-line characters between each entry, to maintain human readability.
     */
    private void writeLine(BufferedWriter bw, String s) throws IOException {
        bw.write(s);
        bw.write(Character.MIN_VALUE);
        bw.newLine();
    }

    /**
     * Not thread safe
     */
    public static class Builder {

        private final Path root;
        private final Map<Path, FileState> statesByPath;

        private Builder(Path root, FileSystemState existingState) {
            this.root = root;
            this.statesByPath = new HashMap<>(existingState.statesByPath);
        }

        public void add(FileState fileState) {
            if (fileState.getPath().isAbsolute())
                throw new IllegalArgumentException("Can not add absolute path: " + fileState.getPath());
            statesByPath.put(fileState.getPath(), fileState);
        }

        public FileSystemState build() {
            return new FileSystemState(root, ZonedDateTime.now(), statesByPath);
        }

    }

}

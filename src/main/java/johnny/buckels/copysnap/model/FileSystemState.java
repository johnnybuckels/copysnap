package johnny.buckels.copysnap.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.util.function.Predicate.not;

public record FileSystemState(Info info, Map<Path, FileState> statesByPath) {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public static FileSystemState.Builder builder(Path rootLocation) {
        return new Builder(rootLocation);
    }

    public static FileSystemState.Builder builder(FileSystemState existingState) {
        return new Builder(existingState.info().rootLocation(), existingState.statesByPath);
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
        Info info = readInfo(is);
        FileSystemState.Builder builder = FileSystemState.builder(info.rootLocation());
        Optional<String> nextLineOpt;
        while ((nextLineOpt = readUntilNextNull(is)).isPresent()) {
            FileState fs = FileState.deserialize(nextLineOpt.get());
            builder.add(fs);
        }
        return builder.build();
    }

    /**
     * Reads a file of the form
     * <p>
     * #ROOT_PATH
     * #DATE
     * </p>
     */
    // TODO: remove Info from this object entirely.
    public static FileSystemState.Info readInfo(InputStream is) throws IOException {
        String rootPathLine = readUntilNextNull(is).orElseThrow(() -> new IllegalStateException("Could not read file system state: Could not retrieve first line"));
        String created = readUntilNextNull(is).orElseThrow(() -> new IllegalStateException("Could not read file system state: Could not retrieve second line"));
        return new Info(Path.of(rootPathLine), ZonedDateTime.parse(created, DATE_TIME_FORMATTER), -1);
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

    Set<FileState> getStatesView() {
        return new HashSet<>(statesByPath.values());
    }

    public Optional<FileState> get(Path relativePath) {
        return Optional.ofNullable(statesByPath.get(relativePath));
    }

    /**
     * @return A new state with all states from this that are contained in the specified paths.
     */
    public FileSystemState newByRemovingMissing(Set<Path> otherPaths) {
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

    /**
     * Returns new object identical to this but with the specified root path.
     */
    public FileSystemState withRootLocation(Path newRootLocation) {
        return new FileSystemState(new Info(newRootLocation, ZonedDateTime.now(), statesByPath.size()), statesByPath);
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
    // TODO. Change to use OutputStream and create Writer in this method.
    public void write(Writer writer) throws IOException {
        writeLine(writer, info.rootLocation().toString());
        writeLine(writer, info.created().format(DATE_TIME_FORMATTER));
        for (FileState fileState : statesByPath.values()) {
            writeLine(writer, fileState.serialize());
        }
        writer.flush();
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

    /**
     * Not thread safe
     */
    public static class Builder {

        private final Path rootLocation;
        private final Map<Path, FileState> statesByPath;

        private Builder(Path rootLocation) {
            this(rootLocation, Map.of());
        }

        private Builder(Path rootLocation, Map<Path, FileState> statesByPath) {
            this.rootLocation = rootLocation;
            this.statesByPath = new HashMap<>(statesByPath);
        }

        public Builder add(FileState fileState) {
            if (fileState.getPath().isAbsolute())
                throw new IllegalArgumentException("Can not add absolute path: " + fileState.getPath());
            statesByPath.put(fileState.getPath(), fileState);
            return this;
        }

        public Builder remove(Path path) {
            statesByPath.remove(path);
            return this;
        }

        public FileSystemState build() {
            return new FileSystemState(new Info(rootLocation, ZonedDateTime.now(), statesByPath.size()), Collections.unmodifiableMap(statesByPath));
        }

    }

    public record Info(Path rootLocation, ZonedDateTime created, int itemCount) {}

}

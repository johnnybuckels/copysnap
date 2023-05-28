package johnny.buckels.copysnap.model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class FileSystemState {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final char FILE_ENTRY_SEPARATOR_CHAR = Character.MIN_VALUE;

    private final Path rootPath;
    private final ZonedDateTime created;
    /**
     * Contains relative FileStates w.r.t. {@link #rootPath}.
     * The absolute paths may be obtained by resolving these FileState's paths against {@link #rootPath}.
     */
    private final Set<FileState> states;

    public static FileSystemState empty() {
        return new FileSystemState(null, ZonedDateTime.now(), Set.of());
    }

    public static FileSystemState.Builder builder(Path rootPath) {
        return new Builder(rootPath);
    }

    /**
     * Reads a file of the form
     * <p>
     *      #ROOT_PATH
     *      #DATE
     *      #HASH;#PATH
     *      ...
     *      #HASH;#PATH
     * </p>
     */
    public static FileSystemState read(Path path) {
        FileSystemState.Builder builder;
        try (BufferedReader br = Files.newBufferedReader(path)) {
            // read first two lines
            builder = FileSystemState.builder(Path.of(readNextEntry(br).orElseThrow()));
            readNextEntry(br).orElseThrow(); // reading 'created'-line which is currently unused
            // read remaining lines
            Optional<String> nextEntry;
            while ((nextEntry = readNextEntry(br)).isPresent()) {
                FileState fs = FileState.parse(nextEntry.get());
                builder.add(fs);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read FileState: " + e, e);
        }
        return builder.build();
    }

    /**
     * Entries are supposed to be surrounded by {@link #FILE_ENTRY_SEPARATOR_CHAR}.
     */
    private static Optional<String> readNextEntry(BufferedReader is) throws IOException {
        skipUntilNextNull(is);
        return readUntilNextNull(is);
    }

    private static Optional<String> readUntilNextNull(BufferedReader is) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        do {
            c = is.read();
            if (c < 0)
                return Optional.empty();
            sb.appendCodePoint(c);
        } while (c != FILE_ENTRY_SEPARATOR_CHAR);
        String substring = sb.substring(0, sb.length() - 1);
        return Optional.of(substring);
    }

    private static void skipUntilNextNull(BufferedReader is) throws IOException {
        int c;
        do {
            c = is.read();
            if (c < 0)
                return;
        } while (c != FILE_ENTRY_SEPARATOR_CHAR);
    }

    public FileSystemState(Path rootPath, ZonedDateTime created, Set<FileState> states) {
        this.rootPath = rootPath;
        this.created = created;
        this.states = states;
    }

    public Path getRootPath() {
        return rootPath;
    }

    public ZonedDateTime getCreated() {
        return created;
    }

    /**
     * @return view of contained states.
     */
    public Set<FileState> getStates() {
        return Collections.unmodifiableSet(states);
    }

    /**
     * Returns new object identical to this but with the specified root path.
     */
    public FileSystemState switchRootTo(Path newRoot) {
        return new FileSystemState(newRoot, created, states);
    }

    /**
     * Writes a file of the form
     * <p>
     *      #ROOT_PATH
     *      #DATE
     *      #HASH;#PATH
     *      ...
     *      #HASH;#PATH
     * </p>
     */
    public void writeTo(Path path) {
        try (BufferedWriter bw = Files.newBufferedWriter(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writeString(bw, rootPath.toString());
            writeString(bw, created.format(DATE_TIME_FORMATTER));
            for (FileState fileState : states) {
                writeString(bw, fileState.toStringRepresentation());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write FileSystemState: " + e, e);
        }
    }

    /**
     * Each line is surrounded by {@link #FILE_ENTRY_SEPARATOR_CHAR}.
     * When parsing the file, line-wise reading could lead to errors since the path-strings may contain new-line
     * characters. To solve that issue, we decide to put "anchors" around each entry.
     * We also put new-line characters between each entry, to maintain human readability.
     */
    private void writeString(BufferedWriter bw, String s) throws IOException {
        bw.write(FILE_ENTRY_SEPARATOR_CHAR);
        bw.write(s);
        bw.write(FILE_ENTRY_SEPARATOR_CHAR);
        bw.newLine();
    }

    public static class Builder {

        private final Path root;
        private final ConcurrentLinkedQueue<FileState> states = new ConcurrentLinkedQueue<>();

        private Builder(Path root) {
            this.root = root;
        }

        public Builder add(FileState fileState) {
            states.add(fileState);
            return this;
        }

        public FileSystemState build() {
            return new FileSystemState(root, ZonedDateTime.now(), new HashSet<>(states));
        }

    }

}

package johnny.buckels.copysnap.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class FileState {

    private static final char STRING_REPRESENTATION_SEPARATOR = ';';

    private final Path path;
    private final Instant lastModified;
    private final String hash;

    public static FileState parse(String fileStateString) {
        String[] parts = fileStateString.split(String.valueOf(STRING_REPRESENTATION_SEPARATOR));
        return new FileState(
                Path.of(parts[parts.length - 1]),
                Instant.parse(parts[1]),
                parts[0]
        );
    }

    public FileState(Path path, Instant lastModified, String hash) {
        this.path = path;
        this.lastModified = lastModified;
        this.hash = hash;
    }

    public Path getPath() {
        return path;
    }

    /**
     * Creates a new object with path relative to the given root.
     */
    public FileState relativize(Path root) {
        if (root.isAbsolute())
            return new FileState(root.relativize(path), lastModified, hash);
        else
            throw new IllegalArgumentException("Root is relative");
    }

    public String toStringRepresentation() {
        return String.join(String.valueOf(STRING_REPRESENTATION_SEPARATOR), List.of(hash, lastModified.toString(), path.toString()));
    }

    // TODO: Remove java object-equals and instead implement a suiting "business" logic elsewhere.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileState fileState = (FileState) o;
        return Objects.equals(path, fileState.path) && Objects.equals(lastModified, fileState.lastModified) && Objects.equals(hash, fileState.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, lastModified, hash);
    }

}

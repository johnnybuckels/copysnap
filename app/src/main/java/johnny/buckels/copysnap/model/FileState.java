package johnny.buckels.copysnap.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileState {

    private static final String FIELD_SERDE_SEPARATOR = ";";
    private static final Pattern FILE_STATE_STRING_PATTERN = Pattern.compile("^(?<checksum>.+);(?<modified>.+);(?<path>(?s).+)$");

    private final Path path;
    private final Instant lastModified;
    private final CheckpointChecksum checksum;

    public static FileState deserialize(String fileStateString) {
        Matcher matcher = FILE_STATE_STRING_PATTERN.matcher(fileStateString);
        if (!matcher.find())
            throw new IllegalArgumentException("Could not create file state from string %s: Does not match pattern %s".formatted(fileStateString, FILE_STATE_STRING_PATTERN));
        CheckpointChecksum checksum = CheckpointChecksum.deserialize(matcher.group("checksum"));
        Instant modified = Instant.parse(matcher.group("modified"));
        Path path = Path.of(matcher.group("path"));
        return new FileState(path, modified, checksum);
    }

    public FileState(Path relPath, Instant lastModified, CheckpointChecksum checksum) {
        if (relPath.isAbsolute())
            throw new IllegalArgumentException("Given path is not relative: " + relPath);
        this.path = relPath;
        this.lastModified = lastModified;
        this.checksum = checksum;
    }

    public Path getPath() {
        return path;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public CheckpointChecksum getChecksum() {
        return checksum;
    }

    public String serialize() {
        return String.join(FIELD_SERDE_SEPARATOR, List.of(checksum.serialize(), lastModified.toString(), path.toString()));
    }

    // TODO: Remove java object-equals and instead implement a suiting "business" logic elsewhere.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileState fileState = (FileState) o;
        return Objects.equals(path, fileState.path) && Objects.equals(lastModified, fileState.lastModified) && Objects.equals(checksum, fileState.checksum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, lastModified, checksum);
    }

}

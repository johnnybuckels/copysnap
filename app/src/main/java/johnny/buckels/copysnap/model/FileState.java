package johnny.buckels.copysnap.model;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

public class FileState {

    private static final char STRING_REPRESENTATION_SEPARATOR = ';';
    private static final char BYTE_HASH_SEPARATOR = ',';

    private final Path path;
    private final byte[] hash;

    public static FileState parse(String fileStateString) {
        int separatorPos = fileStateString.indexOf(STRING_REPRESENTATION_SEPARATOR);
        return new FileState(
                Path.of(fileStateString.substring(separatorPos + 1)),
                deserializeHash(fileStateString.substring(0, separatorPos))
        );
    }

    public FileState(Path path, byte[] hash) {
        this.path = path;
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
            return new FileState(root.relativize(path), hash);
        else
            throw new IllegalArgumentException("Root is relative");
    }

    public String toStringRepresentation() {
        return serializeHash() + STRING_REPRESENTATION_SEPARATOR + path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileState fileState = (FileState) o;
        return path.equals(fileState.path) && Arrays.equals(hash, fileState.hash);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(path);
        result = 31 * result + Arrays.hashCode(hash);
        return result;
    }

    private String serializeHash() {
        StringBuilder sb = new StringBuilder();
        for (byte b : hash)
            sb.append(b).append(BYTE_HASH_SEPARATOR);
        return sb.substring(0, sb.length() - 1);
    }

    /**
     * -1,45,-127,6 -> new byte[] {-1, 45, -127, 6}
     */
    private static byte[] deserializeHash(String s) {
        byte[] bytes = new byte[s.getBytes().length]; // upper limit
        int readBytes = 0;
        int start = 0;
        int end;
        while (start < s.length()) {
            end = s.indexOf(BYTE_HASH_SEPARATOR, start);
            if (end < 0)
                end = s.length();
            bytes[readBytes++] = Byte.parseByte(s.substring(start, end));
            start = end + 1;
        }
        return Arrays.copyOf(bytes, readBytes);
    }

}

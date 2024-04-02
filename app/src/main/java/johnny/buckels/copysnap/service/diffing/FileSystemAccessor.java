package johnny.buckels.copysnap.service.diffing;

import johnny.buckels.copysnap.model.CheckpointChecksum;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public interface FileSystemAccessor {

    static FileSystemAccessor newDefaultAccessor() {
        return new DefaultFileSystemAccessor();
    }

    Instant getLastModifiedTime(Path p) throws IOException;

    boolean areChecksumsEqual(CheckpointChecksum expectedChecksum, Path p) throws IOException;

    class DefaultFileSystemAccessor implements FileSystemAccessor {

        public Instant getLastModifiedTime(Path p) throws IOException {
            return Files.getLastModifiedTime(p).toInstant();
        }

        public boolean areChecksumsEqual(CheckpointChecksum expectedChecksum, Path p) throws IOException {
            try (InputStream is = Files.newInputStream(p)) {
                return expectedChecksum.hasSameChecksum(is);
            }
        }

    }

}

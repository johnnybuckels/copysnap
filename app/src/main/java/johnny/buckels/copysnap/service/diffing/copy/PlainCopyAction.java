package johnny.buckels.copysnap.service.diffing.copy;

import johnny.buckels.copysnap.model.CheckpointChecksum;
import johnny.buckels.copysnap.model.FileState;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Optional;

public class PlainCopyAction extends AbstractCopyAction {

    public PlainCopyAction(Path sourceRoot, Path destinationRoot, Path relPath) {
        super(sourceRoot, destinationRoot, relPath);
    }

    @Override
    public Optional<FileState> perform() throws IOException {
        CheckpointChecksum checksum;
        Instant lastModified;
        Path absSource = sourceRoot.resolve(relPath);
        Path absDestination = destinationRoot.resolve(relPath);

        createParentDirs(absDestination);
        try (
                InputStream is = Files.newInputStream(absSource);
                OutputStream os = Files.newOutputStream(absDestination, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
        ) {
            checksum = CheckpointChecksum.byTransferring(is, os);
            lastModified = Files.getLastModifiedTime(absSource).toInstant();
            os.flush();
        }
        return Optional.of(new FileState(relPath, lastModified, checksum));
    }

}

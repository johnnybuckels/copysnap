package com.github.johannesbuchholz.copysnap.service.diffing.copy;

import com.github.johannesbuchholz.copysnap.model.state.CheckpointChecksum;
import com.github.johannesbuchholz.copysnap.model.state.FileState;
import com.github.johannesbuchholz.copysnap.service.diffing.FileSystemAccessor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

public class PlainCopyAction extends AbstractCopyAction {

    public PlainCopyAction(Path sourceRootLocation, Path destinationRootLocation, Path relPath) {
        super(sourceRootLocation, destinationRootLocation, relPath);
    }

    @Override
    public Optional<FileState> perform(FileSystemAccessor fsa) throws IOException {
        CheckpointChecksum checksum;
        Instant lastModified;
        Path absSource = sourceRootLocation.resolve(relPath);
        Path absDestination = destinationRootLocation.resolve(relPath);

        createParentDirs(absDestination, fsa);
        try (
                InputStream is = fsa.createNewInputStream(absSource);
                OutputStream os = fsa.createNewOutputStream(absDestination)
        ) {
            checksum = CheckpointChecksum.byTransferring(is, os);
            lastModified = Files.getLastModifiedTime(absSource).toInstant();
            os.flush();
        }
        return Optional.of(new FileState(relPath, lastModified, checksum));
    }

}

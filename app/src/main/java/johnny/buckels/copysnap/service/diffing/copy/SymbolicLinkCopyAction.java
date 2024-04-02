package johnny.buckels.copysnap.service.diffing.copy;

import johnny.buckels.copysnap.model.FileState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class SymbolicLinkCopyAction extends AbstractCopyAction {

    public SymbolicLinkCopyAction(Path sourceRoot, Path destinationRoot, Path relPath) {
        super(sourceRoot, destinationRoot, relPath);
    }

    @Override
    public Optional<FileState> perform() throws IOException {
        Path absSource = sourceRoot.resolve(relPath);
        Path absDestination = destinationRoot.resolve(relPath);
        createParentDirs(absDestination);
        Files.createSymbolicLink(absDestination, absSource);
        return Optional.empty();
    }

}

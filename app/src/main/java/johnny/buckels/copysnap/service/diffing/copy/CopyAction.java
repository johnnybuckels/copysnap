package johnny.buckels.copysnap.service.diffing.copy;

import johnny.buckels.copysnap.model.FileState;

import java.io.IOException;
import java.util.Optional;

@FunctionalInterface
public interface CopyAction {

    /**
     * @return The FileState of the copied file if available.
     * The path may be absolute as copy actions operate on absolute paths.
     */
    Optional<FileState> perform() throws IOException;

}

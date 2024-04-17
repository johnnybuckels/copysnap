package johnny.buckels.copysnap.service.diffing.copy;

import johnny.buckels.copysnap.model.FileState;
import johnny.buckels.copysnap.service.diffing.FileSystemAccessor;

import java.io.IOException;
import java.util.Optional;

@FunctionalInterface
public interface CopyAction {

    /**
     * @return The FileState of the copied file if available.
     */
    Optional<FileState> perform(FileSystemAccessor fsa) throws IOException;

}

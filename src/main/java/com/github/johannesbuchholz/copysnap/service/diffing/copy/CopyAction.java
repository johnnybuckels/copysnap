package com.github.johannesbuchholz.copysnap.service.diffing.copy;

import com.github.johannesbuchholz.copysnap.model.FileState;
import com.github.johannesbuchholz.copysnap.service.diffing.FileSystemAccessor;

import java.io.IOException;
import java.util.Optional;

@FunctionalInterface
public interface CopyAction {

    /**
     * @return The FileState of the copied file if available.
     */
    Optional<FileState> perform(FileSystemAccessor fsa) throws IOException;

}

package com.github.johannesbuchholz.copysnap.service.diffing.copy;

import com.github.johannesbuchholz.copysnap.model.state.FileState;
import com.github.johannesbuchholz.copysnap.service.diffing.FileSystemAccessor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public class SymbolicLinkCopyAction extends AbstractCopyAction {

    public SymbolicLinkCopyAction(Path sourceRootLocation, Path destinationRootLocation, Path relPath) {
        super(sourceRootLocation, destinationRootLocation, relPath);
    }

    @Override
    public Optional<FileState> perform(FileSystemAccessor fsa) throws IOException {
        Path absSource = sourceRootLocation.resolve(relPath);
        Path absDestination = destinationRootLocation.resolve(relPath);
        createParentDirs(absDestination, fsa);
        fsa.createSymbolicLink(absDestination, absSource);
        return Optional.empty();
    }

}

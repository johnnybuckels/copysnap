package com.github.johannesbuchholz.copysnap.service.diffing.copy;

import com.github.johannesbuchholz.copysnap.model.FileState;
import com.github.johannesbuchholz.copysnap.service.diffing.FileSystemAccessor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public class SymbolicLinkCopyAction extends AbstractCopyAction {

    public SymbolicLinkCopyAction(Path sourceRoot, Path destinationRoot, Path relPath) {
        super(sourceRoot, destinationRoot, relPath);
    }

    @Override
    public Optional<FileState> perform(FileSystemAccessor fsa) throws IOException {
        Path absSource = sourceRoot.resolve(relPath);
        Path absDestination = destinationRoot.resolve(relPath);
        createParentDirs(absDestination, fsa);
        fsa.createSymbolicLink(absDestination, absSource);
        return Optional.empty();
    }

}

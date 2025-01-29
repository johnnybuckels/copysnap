package com.github.johannesbuchholz.copysnap.service.diffing;

import com.github.johannesbuchholz.copysnap.logging.AbstractLogProducer;
import com.github.johannesbuchholz.copysnap.logging.Level;
import com.github.johannesbuchholz.copysnap.logging.ProgressConsolePrinter;
import com.github.johannesbuchholz.copysnap.model.Root;
import com.github.johannesbuchholz.copysnap.model.state.FileSystemState;
import com.github.johannesbuchholz.copysnap.service.diffing.copy.CopyAction;
import com.github.johannesbuchholz.copysnap.service.diffing.copy.PlainCopyAction;
import com.github.johannesbuchholz.copysnap.service.diffing.copy.SymbolicLinkCopyAction;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

public record FileSystemDiff(
        Root sourceRoot,
        FileSystemState oldStatesOfNotDeletedFiles,
        FileSystemNode diffTree,
        Statistics statistics
) {

    /**
     * @param destination the directory where the copy of the filesystem should reside in.
     * @param oldRootLocation the directory where the old file system has been stored in. Can be {@code null}.
     */
    public Actions computeCopyActions(Path destination, Path oldRootLocation) {
        Set<CopyAction> copyActions = new HashSet<>();
        for (FileSystemNode file : diffTree.getLeafs()) {
            if (file.isChanged()) {
                copyActions.add(new PlainCopyAction(sourceRoot.rootDirLocation(), destination, file.getPath()));
            } else {
                Path upperMostUnchanged = file.getUppermostUnchanged().getPath();
                copyActions.add(new SymbolicLinkCopyAction(oldRootLocation, destination, upperMostUnchanged));
            }
        }
        return new Actions(copyActions);
    }

    public Actions plainCopiesOnly(Path destination) {
        List<CopyAction> plainCopyActions = diffTree.getLeafs().stream()
                .map(FileSystemNode::getPath)
                .map(p -> new PlainCopyAction(sourceRoot.rootDirLocation(), destination, p))
                .collect(Collectors.toList());
        return new Actions(plainCopyActions);
    }

    record Statistics(int newCount, int removedCount, int changedCount, int unchangedCount, int ignoredCount, int errorCount) {
        @Override
        public String toString() {
            return """
                    File count statistics:
                        new: %s
                        changed: %s
                        removed: %s
                        unchanged: %s
                        ignored: %s
                        erroneous: %s"""
                    .formatted(newCount, changedCount, removedCount, unchangedCount, ignoredCount, errorCount);
        }
    }

    public class Actions extends AbstractLogProducer {

        private static final ProgressConsolePrinter PROGRESS_CONSOLE_PRINTER = new ProgressConsolePrinter("Writing files");

        private final Collection<CopyAction> copyActions;

        private Actions(Collection<CopyAction> copyActions) {
            this.copyActions = copyActions;
        }

        /**
         * @return The new file system state.
         */
        public FileSystemState apply(FileSystemAccessor fsa) {
            ZonedDateTime start = ZonedDateTime.now();
            logTaskStart(Level.INFO, "Applying copy actions", start, "count", copyActions.size());
            int performedCount = 0;
            PROGRESS_CONSOLE_PRINTER.update(performedCount, copyActions.size());
            FileSystemState.Builder newStateBuilder = FileSystemState.builder(oldStatesOfNotDeletedFiles);
            for (CopyAction copyAction : new TreeSet<>(copyActions)) {
                log(Level.DEBUG, "Apply %s".formatted(copyAction));
                try {
                    copyAction.perform(fsa)
                            .ifPresent(newStateBuilder::add);
                } catch (IOException e) {
                    String errorMsg = "Could not apply copy action " + copyAction + ": " + e;
                    log(Level.ERROR, errorMsg);
                    logStacktrace(Level.DEBUG, e);
                }
                PROGRESS_CONSOLE_PRINTER.update(++performedCount, copyActions.size());
            }
            PROGRESS_CONSOLE_PRINTER.newLine();
            logTaskEnd(Level.INFO,  "Done applying copy actions", Duration.between(start, ZonedDateTime.now()));
            return newStateBuilder.build();
        }

        public Set<CopyAction> getActions() {
            return new HashSet<>(copyActions);
        }

    }


}

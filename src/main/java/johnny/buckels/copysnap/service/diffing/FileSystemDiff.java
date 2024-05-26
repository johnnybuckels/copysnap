package johnny.buckels.copysnap.service.diffing;

import johnny.buckels.copysnap.model.FileSystemState;
import johnny.buckels.copysnap.model.Root;
import johnny.buckels.copysnap.service.diffing.copy.CopyAction;
import johnny.buckels.copysnap.service.diffing.copy.PlainCopyAction;
import johnny.buckels.copysnap.service.diffing.copy.SymbolicLinkCopyAction;
import johnny.buckels.copysnap.service.logging.AbstractLogProducer;
import johnny.buckels.copysnap.service.logging.Level;
import johnny.buckels.copysnap.service.logging.ProgressConsolePrinter;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;


public record FileSystemDiff(
        Root sourceRoot,
        FileSystemState stillExistingFiles,
        FileSystemNode diffTree,
        DiffCounts counts
) {

    /**
     * @param destination the directory where the copy of the filesystem should reside in.
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

    record DiffCounts(int newCount, int removedCount, int changedCount, int unchangedCount, int errorCount) {}

    public class Actions extends AbstractLogProducer {

        private static final ProgressConsolePrinter PROGRESS_CONSOLE_PRINTER = new ProgressConsolePrinter("Writing files");

        private final Set<CopyAction> copyActions;

        private Actions(Set<CopyAction> copyActions) {
            this.copyActions = copyActions;
        }

        /**
         * @return The new file system state.
         */
        public FileSystemState apply(FileSystemAccessor fsa) {
            ZonedDateTime start = ZonedDateTime.now();
            log(Level.INFO, "Applying copy actions - started: %s, count: %s".formatted(start.toLocalDateTime().truncatedTo(ChronoUnit.SECONDS), copyActions.size()));
            int performedCount = 0;
            PROGRESS_CONSOLE_PRINTER.update(performedCount, copyActions.size());
            FileSystemState.Builder newStateBuilder = FileSystemState.builder(stillExistingFiles);
            for (CopyAction copyAction : copyActions) {
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
            log(Level.INFO, "Done applying copy actions (%s ms)".formatted(Duration.between(start, ZonedDateTime.now()).toMillis()));
            return newStateBuilder.build();
        }

        public Set<CopyAction> getActions() {
            return new HashSet<>(copyActions);
        }

    }


}

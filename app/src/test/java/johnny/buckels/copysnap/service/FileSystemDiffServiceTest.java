package johnny.buckels.copysnap.service;

import johnny.buckels.copysnap.model.FileState;
import johnny.buckels.copysnap.model.FileSystemState;
import johnny.buckels.copysnap.service.diffing.FileSystemDiff;
import johnny.buckels.copysnap.service.diffing.FileSystemDiffService;
import johnny.buckels.copysnap.service.diffing.copy.CopyAction;
import johnny.buckels.copysnap.service.diffing.copy.PlainCopyAction;
import johnny.buckels.copysnap.service.diffing.copy.SymbolicLinkCopyAction;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileSystemDiffServiceTest {

    @Test
    public void test_copyActions_plainCopy() {
        Path rootNew = Path.of("/x/y/z");
        Path file = Path.of("r/a/b/c/f");
        Path rootOld = Path.of("/p/q/rold");
        Path destination = Path.of("/p/q/rnew");

        // and given: new (current) file state
        byte[] hashNew = {0};
        FileState stateNew = new FileState(file, hashNew);
        FileSystemState.Builder builderNew = FileSystemState.builder(rootNew);
        builderNew.add(stateNew);
        FileSystemState fssNew = builderNew.build();

        // and given: old file state
        byte[] hashOld = {1};
        FileState stateOld = new FileState(file, hashOld);
        FileSystemState.Builder builderOld = FileSystemState.builder(rootOld);
        builderOld.add(stateOld);
        FileSystemState fssOld = builderOld.build();

        // when
        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(fssNew, fssOld);
        FileSystemDiff fileSystemDiff = fileSystemDiffService.computeDiff();
        List<CopyAction> copyActions = new ArrayList<>(fileSystemDiff.computeCopyActions(destination));

        // then
        Path expectedCopyLocation = Path.of("/p/q/rnew/r/a/b/c/f");
        Path expectedCopySource = rootNew.resolve(file);
        CopyAction expectedAction = new PlainCopyAction(expectedCopySource, expectedCopyLocation);
        assertEquals(1, copyActions.size());
        assertEquals(expectedAction, copyActions.get(0));
    }

    @Test
    public void test_copyActions_aliasCopy() {
        Path rootNew = Path.of("/x/y/z");
        Path fileNew = Path.of("r/a/b/c/f");
        Path rootOld = Path.of("/p/q/rold");
        Path fileOld = Path.of("r/a/b/c/f");
        Path destination = Path.of("/p/q/rnew");

        // and given: new (current) file state
        byte[] hashNew = {0};
        FileState stateNew = new FileState(fileNew, hashNew);
        FileSystemState.Builder builderNew = FileSystemState.builder(rootNew);
        builderNew.add(stateNew);
        FileSystemState fssNew = builderNew.build();

        // and given: old file state
        byte[] hashOld = {0};
        FileState stateOld = new FileState(fileOld, hashOld);
        FileSystemState.Builder builderOld = FileSystemState.builder(rootOld);
        builderOld.add(stateOld);
        FileSystemState fssOld = builderOld.build();

        // when
        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(fssNew, fssOld);
        FileSystemDiff fileSystemDiff = fileSystemDiffService.computeDiff();
        Set<CopyAction> copyActions = fileSystemDiff.computeCopyActions(destination);

        // then
        Path expectedPath = Path.of("/p/q/rnew/r");
        CopyAction expectedAction = new SymbolicLinkCopyAction(Path.of("/p/q/rold/r"), expectedPath);

        assertEquals(Set.of(expectedAction), copyActions);
    }

    @Test
    public void test_copyActions_aliasAndCopy() {
        Path rootNew = Path.of("/x/y/z");
        Path rootOld = Path.of("/p/q/rold");

        Path fileChanged = Path.of("r/a/b/c/f");
        Path fileUnchanged = Path.of("r/a/v/w/F");

        Path destination = Path.of("/p/q/rnew");

        // and given: new (current) file state
        byte[] hashNewChanged = {0};
        byte[] hashNewUnchanged = {9};
        FileState stateNewChanged = new FileState(fileChanged, hashNewChanged);
        FileState stateNewUnchanged = new FileState(fileUnchanged, hashNewUnchanged);
        FileSystemState.Builder builderNew = FileSystemState.builder(rootNew);
        builderNew.add(stateNewChanged);
        builderNew.add(stateNewUnchanged);
        FileSystemState fssNew = builderNew.build();

        // and given: old file state
        byte[] hashOldChanged = {1};
        byte[] hashOldUnchanged = {9};
        FileState stateOldChanged = new FileState(fileChanged, hashOldChanged);
        FileState stateOldUnchanged = new FileState(fileUnchanged, hashOldUnchanged);
        FileSystemState.Builder builderOld = FileSystemState.builder(rootOld);
        builderOld.add(stateOldChanged);
        builderOld.add(stateOldUnchanged);
        FileSystemState fssOld = builderOld.build();

        // when
        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(fssNew, fssOld);
        FileSystemDiff fileSystemDiff = fileSystemDiffService.computeDiff();
        Set<CopyAction> copyActions = fileSystemDiff.computeCopyActions(destination);

        // then
        Path expectedAliasLocation =  Path.of("/p/q/rnew/r/a/v");
        Path expectedAliasTarget =  Path.of("/p/q/rold/r/a/v");
        CopyAction expectedAliasAction = new SymbolicLinkCopyAction(expectedAliasTarget, expectedAliasLocation);
        Path expectedCopySource = rootNew.resolve(fileChanged);
        Path expectedCopyLocation = Path.of("/p/q/rnew/r/a/b/c/f");
        CopyAction expectedCopyAction = new PlainCopyAction(expectedCopySource, expectedCopyLocation);

        assertEquals(Set.of(expectedCopyAction, expectedAliasAction), copyActions);
    }

    /**
     * CURRENT
     * /x/y/z/
     *  tmp/
     *      d/
     *          file.txt (changed)
     * OLD
     * /p/q/rold/
     *  tmp/
     *      d/
     *          d2/
     *              fileOld.txt
     *          file.txt
     * EXPECT SNAPSHOT
     * /p/q/rnew/
     *  tmp/
     *      d/
     *          file.txt (direct copy)
     */
    @Test
    public void test_copyAction_deleteOne_OneChanged_expectCopy() {
        Path rootNew = Path.of("/x/y/z");
        Path rootOld = Path.of("/p/q/rold");
        Path destination = Path.of("/p/q/rnew");

        Path fileOld = Path.of("tmp/d/d2/fileOld.txt");
        Path fileChanged = Path.of("tmp/d/file.txt");


        // and given: new (current) file state
        FileState hashFileChangedCurrent = new FileState(fileChanged, new byte[] {9});
        FileSystemState.Builder builderNew = FileSystemState.builder(rootNew);
        builderNew.add(hashFileChangedCurrent);
        FileSystemState fssNew = builderNew.build();

        // and given: old file state
        FileState hashFileOld = new FileState(fileOld, new byte[] {0});
        FileState hashFileChanged = new FileState(fileChanged, new byte[] {0});
        FileSystemState.Builder builderOld = FileSystemState.builder(rootOld);
        builderOld.add(hashFileOld);
        builderOld.add(hashFileChanged);
        FileSystemState fssOld = builderOld.build();

        // when
        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(fssNew, fssOld);
        FileSystemDiff fileSystemDiff = fileSystemDiffService.computeDiff();
        Set<CopyAction> copyActions = fileSystemDiff.computeCopyActions(destination);

        // then
        Path expectedCopySource =  Path.of("/x/y/z/tmp/d/file.txt");
        Path expectedCopyTarget =  Path.of("/p/q/rnew/tmp/d/file.txt");
        CopyAction expectedCopyAction = new PlainCopyAction(expectedCopySource, expectedCopyTarget);

        assertEquals(Set.of(expectedCopyAction), copyActions);
    }

    /**
     * CURRENT
     * /x/y/z/
     *  tmp/
     *      d/
     *          file.txt (unchanged)
     * OLD
     * /p/q/rold/
     *  tmp/
     *      d/
     *          d2/
     *              fileOld.txt
     *          file.txt
     * EXPECT SNAPSHOT
     * /p/q/rnew/
     *  tmp/
     *      d/
     *          file.txt (direct copy)
     */
    @Test
    public void test_copyAction_deleteOne_RemainingUnChanged_expectAliasCopyOnFiles() {
        Path unchangedFile = Path.of("tmp/d/file.txt");
        FileSystemState current = FileSystemState.builder(Path.of("/x/y/z"))
                .add(new FileState(unchangedFile, new byte[] {1}))
                .build();

        Path rootOld = Path.of("/p/q/rold");
        FileSystemState old = FileSystemState.builder(rootOld)
                .add(new FileState(unchangedFile, new byte[] {1}))
                .add(new FileState(Path.of("tmp/d/d2/fileOld.txt"), new byte[] {1}))
                .build();

        // when
        Path destination = Path.of("/p/q/rnew");
        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(current, old);
        FileSystemDiff fileSystemDiff = fileSystemDiffService.computeDiff();
        Set<CopyAction> copyActions = fileSystemDiff.computeCopyActions(destination);

        // then
        Path expectedSymlinkTarget =  rootOld.resolve(unchangedFile);
        Path expectedSymlinkLocation =  destination.resolve(unchangedFile);
        CopyAction expectedCopyAction = new SymbolicLinkCopyAction(expectedSymlinkTarget, expectedSymlinkLocation);

        assertEquals(Set.of(expectedCopyAction), copyActions);
    }




}

package johnny.buckels.copysnap.service;

import johnny.buckels.copysnap.model.CheckpointChecksum;
import johnny.buckels.copysnap.model.FileState;
import johnny.buckels.copysnap.model.FileSystemState;
import johnny.buckels.copysnap.service.diffing.FileSystemAccessor;
import johnny.buckels.copysnap.service.diffing.FileSystemDiff;
import johnny.buckels.copysnap.service.diffing.FileSystemDiffService;
import johnny.buckels.copysnap.service.diffing.copy.CopyAction;
import johnny.buckels.copysnap.service.diffing.copy.PlainCopyAction;
import johnny.buckels.copysnap.service.diffing.copy.SymbolicLinkCopyAction;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 *                       FileSystem                        CopySnapCopies
 * rootLocationNew -->   someDir                           ...
 *                           |- Root                          |- 2024-02-23   <---- rootLocationOld
 *                               |-...                            |- Root
 *                               |-...                                |-...
 *                                                                    |-...
 *                                                            |- 2024-04-04     <---- destination
 *                                                                |- (about to copy here...)
 */
public class FileSystemDiffServiceTest {

    private record TestFileSystemAccessor(
            Map<Path, Instant> lastModified,
            Map<Path, CheckpointChecksum> checksums
    ) implements FileSystemAccessor {

        @Override
            public Instant getLastModifiedTime(Path p) {
                return Optional.ofNullable(lastModified.get(p)).orElseThrow();
            }

            @Override
            public boolean areChecksumsEqual(CheckpointChecksum expectedChecksum, Path p) {
                return Optional.ofNullable(checksums.get(p)).map(expectedChecksum::equals).orElseThrow();
            }

        }

    @Test
    public void test_copyActions_plainCopy() {
        Path rootNew = Path.of("/x/y/z");
        Path file = Path.of("r/a/b/c/f");
        Path rootOld = Path.of("/p/q/rold");
        Path destination = Path.of("/p/q/rnew");
        Instant time = Instant.now();

        // and given: new (current) file state
        CheckpointChecksum hashNew = checksum("newHash");

        // and given: old file state
        CheckpointChecksum hashOld = checksum("oldHash");
        FileState stateOld = new FileState(file, time, hashOld);
        FileSystemState.Builder builderOld = FileSystemState.builder(rootOld);
        builderOld.add(stateOld);
        FileSystemState fssOld = builderOld.build();

        // when
        TestFileSystemAccessor fsa = new TestFileSystemAccessor(
                Map.of(rootNew.resolve(file), time.plusSeconds(1)),
                Map.of(rootNew.resolve(file), hashNew)
        );

        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(fsa);
        FileSystemDiff fileSystemDiff = fileSystemDiffService.computeDiff(rootNew, List.of(file), fssOld);
        Set<CopyAction> copyActions = fileSystemDiff.computeCopyActions(destination);

        // then
        /*
            expectedCopyLocation: /p/q/rnew/r/a/b/c/f
            expectedCopySource: rootNew.resolve(file)
         */
        CopyAction expectedAction = new PlainCopyAction(rootNew, destination, file);
        assertEquals(Set.of(expectedAction), copyActions);
        assertEquals(new FileSystemDiff.DiffCounts(0, 0, 1, 0, 0), fileSystemDiff.getCounts());

    }


    @Test
    public void test_copyActions_aliasCopy() {
        Path rootNew = Path.of("/x/y/z");
        Path file = Path.of("r/a/b/c/f");
        Path rootOld = Path.of("/p/q/rold");
        Path destination = Path.of("/p/q/rnew");
        Instant time = Instant.now();

        // and given: new (current) file state
        CheckpointChecksum hashNew = checksum("{0}");

        // and given: old file state
        CheckpointChecksum hashOld = checksum("{0}");
        FileState stateOld = new FileState(file, time, hashOld);
        FileSystemState.Builder builderOld = FileSystemState.builder(rootOld);
        builderOld.add(stateOld);
        FileSystemState fssOld = builderOld.build();

        // when
        TestFileSystemAccessor fsa = new TestFileSystemAccessor(
                Map.of(rootNew.resolve(file), time.plusSeconds(1)),
                Map.of(rootNew.resolve(file), hashNew)
        );
        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(fsa);
        FileSystemDiff fileSystemDiff = fileSystemDiffService.computeDiff(rootNew, List.of(file), fssOld);
        Set<CopyAction> copyActions = fileSystemDiff.computeCopyActions(destination);

        // then
        /*
            expectedPath: "/p/q/rnew/r"
            No file changed so the uppermost unchanged will be copied with a symbolic link, which is r
         */
        CopyAction expectedAction = new SymbolicLinkCopyAction(rootOld, destination, Path.of("r"));
        assertEquals(Set.of(expectedAction), copyActions);
        assertEquals(new FileSystemDiff.DiffCounts(0, 0, 0, 1, 0), fileSystemDiff.getCounts());

    }

    /**
     * CURRENT
     * /x/y/z/
     *      r/
     *          a/
     *              b/
     *                  c/
     *                      f (changed)
     *              v/
     *                  w/
     *                      F (unchanged)
     * OLD
     * /p/q/rold
     *      r/
     *          a/
     *              b/
     *                  c/
     *                      f (changed)
     *              v/
     *                  w/
     *                      F (unchanged)
     * EXPECT SNAPSHOT
     * /p/q/rnew/
     *      r/
     *          a/
     *              b/
     *                  c/
     *                      f (direct copy)
     *              v/      (alias to /p/q/rold/r/a/v)
     */
    @Test
    public void test_copyActions_aliasAndCopy() {
        Path rootNew = Path.of("/x/y/z");
        Path rootOld = Path.of("/p/q/rold");

        Path fileChanged = Path.of("r/a/b/c/f");
        Path fileUnchanged = Path.of("r/a/v/w/F");

        Path destination = Path.of("/p/q/rnew");

        // and given: new (current) file state
        Instant time = Instant.now();
        CheckpointChecksum hashNewChanged = checksum("0");
        CheckpointChecksum hashNewUnchanged = checksum("9");

        // and given: old file state
        CheckpointChecksum hashOldChanged = checksum("1");
        CheckpointChecksum hashOldUnchanged = checksum("9");
        FileState stateOldChanged = new FileState(fileChanged, time, hashOldChanged);
        FileState stateOldUnchanged = new FileState(fileUnchanged, time, hashOldUnchanged);
        FileSystemState.Builder builderOld = FileSystemState.builder(rootOld);
        builderOld.add(stateOldChanged);
        builderOld.add(stateOldUnchanged);
        FileSystemState fssOld = builderOld.build();

        // when
        TestFileSystemAccessor fsa = new TestFileSystemAccessor(
                Map.of(
                        rootNew.resolve(fileChanged), time.plusSeconds(1),
                        rootNew.resolve(fileUnchanged), time.plusSeconds(1)),
                Map.of(
                        rootNew.resolve(fileChanged), hashNewChanged,
                        rootNew.resolve(fileUnchanged), hashNewUnchanged)
        );
        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(fsa);
        FileSystemDiff fileSystemDiff = fileSystemDiffService.computeDiff(rootNew, List.of(fileChanged, fileUnchanged), fssOld);
        Set<CopyAction> copyActions = fileSystemDiff.computeCopyActions(destination);

        // then
        /*
            expectedAliasLocation: /p/q/rnew/r/a/v
            expectedAliasTarget: /p/q/rold/r/a/v
            No file changed up to the uppermost unchanged directory, which is r/a/v.
         */
        CopyAction expectedAliasAction = new SymbolicLinkCopyAction(rootOld, destination, Path.of("r/a/v"));
        /*
            expectedCopySource: fileChanged
            expectedCopyLocation: /p/q/rnew/r/a/b/c/f
         */
        CopyAction expectedCopyAction = new PlainCopyAction(rootNew, destination, fileChanged);
        assertEquals(Set.of(expectedAliasAction, expectedCopyAction), copyActions);
        assertEquals(new FileSystemDiff.DiffCounts(0, 0, 1, 1, 0), fileSystemDiff.getCounts());
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

        Instant time = Instant.now();

        // and given: new (current) file state
        CheckpointChecksum hashNewChanged = checksum("new byte[] {9}");

        // and given: old file state
        FileState hashFileOld = new FileState(fileOld, time, checksum("new byte[] {0}"));
        FileState hashFileChanged = new FileState(fileChanged, time, checksum("new byte[] {0}"));
        FileSystemState.Builder builderOld = FileSystemState.builder(rootOld);
        builderOld.add(hashFileOld);
        builderOld.add(hashFileChanged);
        FileSystemState fssOld = builderOld.build();

        // when
        TestFileSystemAccessor fsa = new TestFileSystemAccessor(
                Map.of(rootNew.resolve(fileChanged), time.plusSeconds(1)),
                Map.of(rootNew.resolve(fileChanged), hashNewChanged)
        );
        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(fsa);
        FileSystemDiff fileSystemDiff = fileSystemDiffService.computeDiff(rootNew, List.of(fileChanged), fssOld);
        Set<CopyAction> copyActions = fileSystemDiff.computeCopyActions(destination);

        // then
        /*
            expectedCopySource: /x/y/z/tmp/d/file.txt
            expectedCopyTarget: /p/q/rnew/tmp/d/file.txt
         */
        CopyAction expectedCopyAction = new PlainCopyAction(rootNew, destination, fileChanged);
        assertEquals(Set.of(expectedCopyAction), copyActions);
        assertEquals(new FileSystemDiff.DiffCounts(0, 1, 1, 0, 0), fileSystemDiff.getCounts());
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
     *          file.txt (symlink)
     */
    @Test
    public void test_copyAction_deleteOne_RemainingUnchanged_expectAliasCopyOnFiles() {
        Instant time = Instant.now();

        Path rootNew = Path.of("/x/y/z");
        Path rootOld = Path.of("/p/q/rold");
        Path unchangedFile = Path.of("tmp/d/file.txt");
        Path noLongerPresentFileOld = Path.of("tmp/d/d2/fileOld.txt");

        CheckpointChecksum unchangedChecksum = checksum("new byte[] {1}");

        FileSystemState.Builder builderOld = FileSystemState.builder(rootOld);
        builderOld.add(new FileState(unchangedFile, time, unchangedChecksum));
        builderOld.add(new FileState(noLongerPresentFileOld, time, unchangedChecksum));
        FileSystemState fssOld = builderOld.build();

        Path destination = Path.of("/p/q/rnew");

        // when
        TestFileSystemAccessor fsa = new TestFileSystemAccessor(
                Map.of(rootNew.resolve(unchangedFile), time.plusSeconds(1)),
                Map.of(rootNew.resolve(unchangedFile), unchangedChecksum)
        );
        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(fsa);
        FileSystemDiff fileSystemDiff = fileSystemDiffService.computeDiff(rootNew, List.of(unchangedFile), fssOld);
        Set<CopyAction> copyActions = fileSystemDiff.computeCopyActions(destination);

        // then
        CopyAction expectedCopyAction = new SymbolicLinkCopyAction(rootOld, destination, unchangedFile);

        assertEquals(Set.of(expectedCopyAction), copyActions);
        assertEquals(new FileSystemDiff.DiffCounts(0, 1, 0, 1, 0), fileSystemDiff.getCounts());
    }

    private CheckpointChecksum checksum(String stringContent) {
        return CheckpointChecksum.from(new ByteArrayInputStream(stringContent.getBytes()));
    }

}

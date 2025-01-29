package com.github.johannesbuchholz.copysnap.service.diffing;

import com.github.johannesbuchholz.copysnap.model.Root;
import com.github.johannesbuchholz.copysnap.model.state.CheckpointChecksum;
import com.github.johannesbuchholz.copysnap.model.state.FileState;
import com.github.johannesbuchholz.copysnap.model.state.FileSystemState;
import com.github.johannesbuchholz.copysnap.service.diffing.copy.CopyAction;
import com.github.johannesbuchholz.copysnap.service.diffing.copy.PlainCopyAction;
import com.github.johannesbuchholz.copysnap.service.diffing.copy.SymbolicLinkCopyAction;
import com.github.johannesbuchholz.copysnap.service.diffing.testutils.TestFileSystemAccessor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Test
    public void test_copyActions_plainCopy() throws IOException {
        Path sourceRootDirectory = Path.of("/x/y/z/r");
        Root sourceRoot = Root.from(sourceRootDirectory);
        Path file = Path.of("r/a/b/c/f");
        Path rootOld = Path.of("/p/q/rold");
        Path destination = Path.of("/p/q/rnew");
        Instant time = Instant.now();

        // and given: new (current) file state
        CheckpointChecksum hashNew = checksum("newHash");

        // and given: old file state
        CheckpointChecksum hashOld = checksum("oldHash");
        FileState stateOld = new FileState(file, time, hashOld);
        FileSystemState.Builder builderOld = FileSystemState.builder();
        builderOld.add(stateOld);
        FileSystemState fssOld = builderOld.build();

        // when
        TestFileSystemAccessor fsa = TestFileSystemAccessor.builder()
                .setLastModified(Map.of(sourceRoot.rootDirLocation().resolve(file), time.plusSeconds(1)))
                .setChecksums(Map.of(sourceRoot.rootDirLocation().resolve(file), hashNew))
                .setPathsByRootDir(Map.of(sourceRoot.pathToRootDir(), List.of(sourceRoot.rootDirLocation().resolve(file))))
                .build();

        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(fsa);
        FileSystemDiff fileSystemDiff = fileSystemDiffService.computeDiff(sourceRoot, fssOld, List.of());
        Set<CopyAction> copyActions = fileSystemDiff.computeCopyActions(destination, rootOld).getActions();

        // then
        /*
            expectedCopyLocation: /p/q/rnew/r/a/b/c/f
            expectedCopySource: rootNew.resolve(file)
         */
        CopyAction expectedAction = new PlainCopyAction(sourceRoot.rootDirLocation(), destination, file);
        assertEquals(Set.of(expectedAction), copyActions);
        assertEquals(new FileSystemDiff.Statistics(0, 0, 1, 0, 0, 0), fileSystemDiff.statistics());
    }


    @Test
    public void test_copyActions_aliasCopy() throws IOException {
        Path sourceRootDir = Path.of("/x/y/z/r");
        Root sourceRoot = Root.from(sourceRootDir);
        Path file = Path.of("r/a/b/c/f");
        Path rootOld = Path.of("/p/q/rold");
        Path destination = Path.of("/p/q/rnew");
        Instant time = Instant.now();

        // and given: new (current) file state
        CheckpointChecksum hashNew = checksum("{0}");

        // and given: old file state
        CheckpointChecksum hashOld = checksum("{0}");
        FileState stateOld = new FileState(file, time, hashOld);
        FileSystemState.Builder builderOld = FileSystemState.builder();
        builderOld.add(stateOld);
        FileSystemState fssOld = builderOld.build();

        // when
        TestFileSystemAccessor fsa = TestFileSystemAccessor.builder()
                .setLastModified(Map.of(sourceRoot.rootDirLocation().resolve(file), time.plusSeconds(1)))
                .setChecksums(Map.of(sourceRoot.rootDirLocation().resolve(file), hashNew))
                .setPathsByRootDir(Map.of(sourceRoot.pathToRootDir(), List.of(sourceRoot.rootDirLocation().resolve(file))))
                .build();
        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(fsa);
        FileSystemDiff fileSystemDiff = fileSystemDiffService.computeDiff(sourceRoot, fssOld, List.of());
        Set<CopyAction> copyActions = fileSystemDiff.computeCopyActions(destination, rootOld).getActions();

        // then
        /*
            expectedPath: "/p/q/rnew/r"
            No file changed so the uppermost unchanged will be copied with a symbolic link, which is r
         */
        CopyAction expectedAction = new SymbolicLinkCopyAction(rootOld, destination, Path.of("r"));
        assertEquals(Set.of(expectedAction), copyActions);
        assertEquals(new FileSystemDiff.Statistics(0, 0, 0, 1, 0, 0), fileSystemDiff.statistics());

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
    public void test_copyActions_aliasAndCopy() throws IOException {
        Path sourceRootDir = Path.of("/x/y/z/r");
        Root sourceRoot = Root.from(sourceRootDir);
        Path rootDirOld = Path.of("/p/q/rold/r");
        Root rootOld = Root.from(rootDirOld);

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
        FileSystemState.Builder builderOld = FileSystemState.builder();
        builderOld.add(stateOldChanged);
        builderOld.add(stateOldUnchanged);
        FileSystemState fssOld = builderOld.build();

        // when
        TestFileSystemAccessor fsa = TestFileSystemAccessor.builder()
                .setLastModified(Map.of(
                        sourceRoot.rootDirLocation().resolve(fileChanged), time.plusSeconds(1),
                        sourceRoot.rootDirLocation().resolve(fileUnchanged), time.plusSeconds(1)))
                .setChecksums(Map.of(
                        sourceRoot.rootDirLocation().resolve(fileChanged), hashNewChanged,
                        sourceRoot.rootDirLocation().resolve(fileUnchanged), hashNewUnchanged))
                .setPathsByRootDir(Map.of(
                        sourceRoot.pathToRootDir(), List.of(
                                sourceRoot.rootDirLocation().resolve(fileChanged),
                                sourceRoot.rootDirLocation().resolve(fileUnchanged))))
                .build();
        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(fsa);
        FileSystemDiff fileSystemDiff = fileSystemDiffService.computeDiff(sourceRoot, fssOld, List.of());
        Set<CopyAction> copyActions = fileSystemDiff.computeCopyActions(destination, rootOld.rootDirLocation()).getActions();

        // then
        /*
            expectedAliasLocation: /p/q/rnew/r/a/v
            expectedAliasTarget: /p/q/rold/r/a/v
            No file changed up to the uppermost unchanged directory, which is r/a/v.
         */
        CopyAction expectedAliasAction = new SymbolicLinkCopyAction(rootOld.rootDirLocation(), destination, Path.of("r/a/v"));
        /*
            expectedCopySource: fileChanged
            expectedCopyLocation: /p/q/rnew/r/a/b/c/f
         */
        CopyAction expectedCopyAction = new PlainCopyAction(sourceRoot.rootDirLocation(), destination, fileChanged);
        assertEquals(Set.of(expectedAliasAction, expectedCopyAction), copyActions);
        assertEquals(new FileSystemDiff.Statistics(0, 0, 1, 1, 0, 0), fileSystemDiff.statistics());
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
    public void test_copyAction_deleteOne_OneChanged_expectCopy() throws IOException {
        Path sourceRootDir = Path.of("/x/y/z/r");
        Root sourceRoot = Root.from(sourceRootDir);
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
        FileSystemState.Builder builderOld = FileSystemState.builder();
        builderOld.add(hashFileOld);
        builderOld.add(hashFileChanged);
        FileSystemState fssOld = builderOld.build();

        // when
        TestFileSystemAccessor fsa = TestFileSystemAccessor.builder()
                .setLastModified(Map.of(sourceRoot.rootDirLocation().resolve(fileChanged), time.plusSeconds(1)))
                .setChecksums(Map.of(sourceRoot.rootDirLocation().resolve(fileChanged), hashNewChanged))
                .setPathsByRootDir(Map.of(sourceRoot.pathToRootDir(), List.of(sourceRoot.rootDirLocation().resolve(fileChanged))))
                .build();
        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(fsa);
        FileSystemDiff fileSystemDiff = fileSystemDiffService.computeDiff(sourceRoot, fssOld, List.of());
        Set<CopyAction> copyActions = fileSystemDiff.computeCopyActions(destination, rootOld).getActions();

        // then
        /*
            expectedCopySource: /x/y/z/tmp/d/file.txt
            expectedCopyTarget: /p/q/rnew/tmp/d/file.txt
         */
        CopyAction expectedCopyAction = new PlainCopyAction(sourceRoot.rootDirLocation(), destination, fileChanged);
        assertEquals(Set.of(expectedCopyAction), copyActions);
        assertEquals(new FileSystemDiff.Statistics(0, 1, 1, 0, 0, 0), fileSystemDiff.statistics());
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
    public void test_copyAction_deleteOne_RemainingUnchanged_expectAliasCopyOnFiles() throws IOException {
        Instant time = Instant.now();

        Path sourceRootDir = Path.of("/x/y/z/r");
        Root sourceRoot = Root.from(sourceRootDir);
        Path rootDirOld = Path.of("/p/q/rold/r");
        Root rootOld = Root.from(rootDirOld);
        Path unchangedFile = Path.of("tmp/d/file.txt");
        Path noLongerPresentFileOld = Path.of("tmp/d/d2/fileOld.txt");

        CheckpointChecksum unchangedChecksum = checksum("new byte[] {1}");

        FileSystemState.Builder builderOld = FileSystemState.builder();
        builderOld.add(new FileState(unchangedFile, time, unchangedChecksum));
        builderOld.add(new FileState(noLongerPresentFileOld, time, unchangedChecksum));
        FileSystemState fssOld = builderOld.build();

        Path destination = Path.of("/p/q/rnew");

        // when
        TestFileSystemAccessor fsa = TestFileSystemAccessor.builder()
                .setLastModified(Map.of(sourceRoot.rootDirLocation().resolve(unchangedFile), time.plusSeconds(1)))
                .setChecksums(Map.of(sourceRoot.rootDirLocation().resolve(unchangedFile), unchangedChecksum))
                .setPathsByRootDir(Map.of(sourceRoot.pathToRootDir(), List.of(sourceRoot.rootDirLocation().resolve(unchangedFile))))
                .build();
        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(fsa);
        FileSystemDiff fileSystemDiff = fileSystemDiffService.computeDiff(sourceRoot, fssOld, List.of());
        Set<CopyAction> copyActions = fileSystemDiff.computeCopyActions(destination, rootOld.rootDirLocation()).getActions();

        // then
        CopyAction expectedCopyAction = new SymbolicLinkCopyAction(rootOld.rootDirLocation(), destination, unchangedFile);

        assertEquals(Set.of(expectedCopyAction), copyActions);
        assertEquals(new FileSystemDiff.Statistics(0, 1, 0, 1, 0, 0), fileSystemDiff.statistics());
    }

    @Test
    void test_withIgnoredFiles() throws IOException {
        // given
        String globPattern1 = "**/*.txt";
        String globPattern2 = "**.yaml";
        List<String> paths = List.of("/x/y/z/file.txt", "/y/z/file.txt", "/y/file.txt", "/y/file.txt", "/x/y", "/x/y/blubb.yaml", "/other-file.txt");

        // when diff on simple file system state:
        //   fsa returns the given list of paths, existing file system state is empty (all paths are "new")
        Path rootDir = Path.of("/");
        TestFileSystemAccessor fileSystemAccessor = TestFileSystemAccessor.builder()
                .setPathsByRootDir(Map.of(rootDir, paths.stream().map(Path::of).toList()))
                .build();

        FileSystemDiffService fileSystemDiffService = new FileSystemDiffService(fileSystemAccessor);
        FileSystemDiff fileSystemDiff = fileSystemDiffService.computeDiff(
                Root.from(rootDir),
                FileSystemState.empty(),
                List.of(globPattern1, globPattern2));

        // then
        Set<Path> remaining = fileSystemDiff.diffTree().getLeafs().stream()
                .map(FileSystemNode::getPath)
                .collect(Collectors.toSet());
        assertEquals(Set.of(Path.of("x/y"), Path.of("other-file.txt")), remaining);

        assertEquals(
                new FileSystemDiff.Statistics(2, 0, 0, 0, 5, 0),
                fileSystemDiff.statistics());
    }

    private CheckpointChecksum checksum(String stringContent) {
        return CheckpointChecksum.from(new ByteArrayInputStream(stringContent.getBytes()));
    }

}

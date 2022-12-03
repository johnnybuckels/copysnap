package johnny.buckels.copysnap.service;

import johnny.buckels.copysnap.model.Context;
import johnny.buckels.copysnap.model.Contexts;
import johnny.buckels.copysnap.model.FileSystemState;
import johnny.buckels.copysnap.service.hashing.ParallelHashingService;

import java.nio.file.Path;

public class SnapshotServiceTest {

    private static final Path TEST_PATH_HDD = Path.of("/home/johannes/Dokumente/DokumenteHDD/Uni Darmstadt");
    private static final Path TEST_PATH = Path.of("/media/johannes/HDD/DokumenteHDD/Java/copysnap-projekte/copysnap/tmp");

    private static final Path TEST_PATH_SNAPSHOTS = Path.of("/media/johannes/HDD/DokumenteHDD/Java/copysnap-projekte/copysnap/tmp_snapshots");

//    @Test
    public void generateSnapshotTest() {
        ParallelHashingService parallelHashingService = new ParallelHashingService(1);
        FileSystemState newState = parallelHashingService.computeState(TEST_PATH);
        FileSystemState oldState = parallelHashingService.computeState(TEST_PATH_SNAPSHOTS.resolve("test1/tmp"));
        new SnapshotService(newState, oldState).createNewSnapshot(TEST_PATH_SNAPSHOTS.resolve("test2"));
    }

//    @Test
    public void generateSnapshotTest_fromContext() {
        Context context = Contexts.createNew(TEST_PATH, TEST_PATH_SNAPSHOTS);
        context.createSnapshot();
    }

}

package johnny.buckels.copysnap.service;

import johnny.buckels.copysnap.model.FileSystemState;
import johnny.buckels.copysnap.service.hashing.ParallelHashingService;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class HashingServiceTest {

    private static final Path TEST_PATH = Path.of("/media/johannes/HDD/DokumenteHDD/Java/copysnap-projekte/copysnap/tmp");

    // Accesses actual file system
//    @Test
    public void testParallelHashing() {
        ParallelHashingService parallelHashingService = new ParallelHashingService();

        long start = System.currentTimeMillis();
        FileSystemState fileSystemState = parallelHashingService.computeState(TEST_PATH);
        System.out.println("Execution time: " + (System.currentTimeMillis() - start) / 1000.);

        assertNotNull(fileSystemState);
        fileSystemState.getStates().forEach(fs -> System.out.println(fs.toStringRepresentation()));
    }

}

package johnny.buckels.copysnap.testutils;

import johnny.buckels.copysnap.model.FileState;

import java.nio.file.Path;
import java.util.Random;
import java.util.stream.Collectors;

public class FileStateGenerator {

    private static final int HASH_SIZE = 16;
    private static final int MAX_PATH_LENGTH = 8;
    private final Random rng;


    public FileStateGenerator() {
        rng = new Random();
    }

    /**
     * File state with root "/".
     */
    public FileState generateFileState() {
        return generateFileState(Path.of("/"));
    }

    public FileState generateFileState(Path root) {
        byte[] bytes = new byte[HASH_SIZE];
        rng.nextBytes(bytes);
        // 97 = 'a', 122 = 'z'
        Path p = root.resolve(
                rng.ints(97, 123)
                        .limit(rng.nextInt(MAX_PATH_LENGTH) + 1)
                        .mapToObj(Character::toChars)
                        .map(String::new)
                        .collect(Collectors.joining("/"))
        );
        return new FileState(p, bytes);
    }

}

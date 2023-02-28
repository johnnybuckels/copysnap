package johnny.buckels.copysnap.testutils;

import johnny.buckels.copysnap.model.FileState;

import java.nio.file.Path;
import java.util.Random;

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
    public FileState generateRandomFileState() {
        return generateRandomFileState(Path.of("/"));
    }

    public FileState generateRandomFileState(Path root) {
        return generateRandomFileState(root, rng.nextInt(MAX_PATH_LENGTH + 1));
    }

    public FileState generateRandomFileState(Path root, int randomPartLength) {
        byte[] bytes = new byte[HASH_SIZE];
        rng.nextBytes(bytes);
        // 97 = 'a', 122 = 'z'
        Path p = root.resolve(getRandomRelativePath(randomPartLength));
        return new FileState(p, bytes);
    }

    /**
     * @return Path.of("g/a/c/a/a/h/i/").
     */
    public Path getRandomRelativePath(int length) {
        return Path.of("", rng.ints(97, 123)
                .limit(length)
                .mapToObj(Character::toChars)
                .map(String::new)
                .toArray(String[]::new));
    }



}

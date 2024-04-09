package johnny.buckels.copysnap.model;

import java.nio.file.Path;
import java.util.Optional;

/**
 * In order to store file hashes with relative paths, we determine the directory where the file system to create
 * hashes from is located in.
 * Example:
 * pathToRootDir: /x/y/z/r
 * rootDirLocation: /x/y/z
 * Actual file hashes: /x/y/z/r/a/b/f1, /x/y/z/r/a/p/q/f2, /x/y/z/r/f3
 * Relative file hashes: r/a/b/f1, r/a/p/q/f2, r/f3
 */
public record Root(Path pathToRootDir, Path rootDirLocation) {

    public static Root from(Path path) {
        if (!path.isAbsolute()) {
            System.out.println("path = " + path);
            throw new IllegalArgumentException("Path to root dir must be absolute: " + path);
        }
        return new Root(path, Optional.ofNullable(path.getParent()).orElse(path));
    }

}

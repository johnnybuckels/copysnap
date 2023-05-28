package johnny.buckels.copysnap.service.hashing;

import johnny.buckels.copysnap.model.FileState;
import johnny.buckels.copysnap.model.FileSystemState;
import johnny.buckels.copysnap.service.exception.HashingException;
import johnny.buckels.copysnap.service.logging.AbstractMessageProducer;
import johnny.buckels.copysnap.service.logging.Message;
import johnny.buckels.copysnap.util.HashUtils;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class ParallelHashingService extends AbstractMessageProducer {

    private static final String MESSAGE_SUBJECT = "Computing hashes";

    /**
     * Computes the hashes of all files inside the target directory and subdirectories potentially in parallel.
     */
    public FileSystemState computeState(Path targetDirectory) {
        /*
        In order to store file hashes with relative paths, we determine the directory where the file system to create
        hashes from is located in.
        Example: targetDirectory: /x/y/z/r
        Actual file hashes: /x/y/z/r/a/b/f1, /x/y/z/r/a/p/q/f2, /x/y/z/r/f3
        Relative file hashes: r/a/b/f1, r/a/p/q/f2, r/f3
         */
        Path rootPath = Objects.requireNonNullElse(targetDirectory.getParent(), targetDirectory);
        AtomicInteger submittedCount = new AtomicInteger();
        AtomicInteger completedCount = new AtomicInteger();
        FileSystemState.Builder fstBuilder = FileSystemState.builder(rootPath);
        try (Stream<Path> files = Files.walk(targetDirectory, FileVisitOption.FOLLOW_LINKS)) {
            files
                    .filter(Files::isRegularFile)
                    .parallel()
                    .forEach(path -> {
                        messageConsumer.consumeMessageOverride(Message.progressInfo(MESSAGE_SUBJECT, completedCount.get(), submittedCount.incrementAndGet()));
                        byte[] hash = HashUtils.computeFileHash(path);
                        FileState fileState = new FileState(path, hash);
                        fstBuilder.add(fileState.relativize(rootPath));
                        messageConsumer.consumeMessageOverride(Message.progressInfo(MESSAGE_SUBJECT, completedCount.incrementAndGet(), submittedCount.get()));
                    });
        } catch (IOException e) {
            throw new HashingException("Could not iterate over directory contents during hashing at " + rootPath + ": " + e.getMessage(), e);
        }
        FileSystemState fileSystemState = fstBuilder.build();
        messageConsumer.newLine();
        return fileSystemState;
    }

}

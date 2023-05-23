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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ParallelHashingService extends AbstractMessageProducer {

    private final int maxPoolSize;

    public ParallelHashingService(int maxPoolSize) {
       this.maxPoolSize = maxPoolSize;
    }

    /**
     * Computes the hashes of all files inside the target directory and subdirectories potentially in parallel.
     */
    public FileSystemState computeState(Path targetDirectory) {
        messageConsumer.consumeMessage(Message.info("Computing file hashes at %s using thread pool of size %s", targetDirectory, maxPoolSize));
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
        List<Path> paths;
        try (Stream<Path> files = Files.walk(targetDirectory, FileVisitOption.FOLLOW_LINKS)) {
            paths = files
                    .filter(Files::isRegularFile)
                    .peek(path -> messageConsumer.consumeMessageOverride(Message.progressInfo(completedCount.get(), submittedCount.incrementAndGet())))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new HashingException("Could not iterate over directory contents during hashing: " + rootPath, e);
        }

        ConcurrentLinkedQueue<FileState> fileStates = new ConcurrentLinkedQueue<>();
        paths.stream()
                .parallel()
                .forEach(path -> {
                    byte[] hash = HashUtils.computeFileHash(path);
                    fileStates.add(new FileState(path, hash));
                    messageConsumer.consumeMessageOverride(Message.progressInfo(completedCount.incrementAndGet(), submittedCount.get()));
                });

        FileSystemState.Builder fstBuilder = FileSystemState.builder(rootPath);
        for (FileState fileState : fileStates) {
            // make path is FileState relative to root
            fstBuilder.add(fileState.relativize(rootPath));
        }
        FileSystemState fileSystemState = fstBuilder.build();
        messageConsumer.newLine();
        return fileSystemState;
    }

}

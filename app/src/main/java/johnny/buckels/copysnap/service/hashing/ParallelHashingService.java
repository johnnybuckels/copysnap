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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ParallelHashingService extends AbstractMessageProducer {

    private final int maxPoolSize;

    public ParallelHashingService(int maxPoolSize) {
       this.maxPoolSize = maxPoolSize;
    }

    /**
     * Computes the hashes of the file or directory and potentially all subdirectories. When encountering directories,
     * this method tries to parallelize work.
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
        HashingTaskRegistry hashingTaskRegistry = new HashingTaskRegistry(maxPoolSize);
        try (Stream<Path> files = Files.walk(targetDirectory, FileVisitOption.FOLLOW_LINKS)) {
            files
                    .filter(Files::isRegularFile)
                    .forEach(hashingTaskRegistry::submitHashTask);
        } catch (IOException e) {
            throw new HashingException("Could not iterate over directory contents during hashing: " + rootPath, e);
        }
        FileSystemState.Builder fstBuilder = FileSystemState.builder(rootPath);
        hashingTaskRegistry.join().stream()
                .map(fs -> fs.relativize(rootPath))  // make path is FileState relative to root
                .forEach(fstBuilder::add);
        FileSystemState fileSystemState = fstBuilder.build();
        messageConsumer.consumeMessage(Message.info("Found %s files.", fileSystemState.getStates().size()));
        return fileSystemState;
    }

    private class HashingTaskRegistry {
        
        private final List<PendingHashResult> allTasks = new ArrayList<>();
        private final ExecutorService executorService;
        private final AtomicInteger submittedCount = new AtomicInteger();
        private final AtomicInteger completedCount = new AtomicInteger();

        private int lastSentSubmittedCount = 0;
        private int lastSentCompletedCount = 0;

        private HashingTaskRegistry(int maxPoolSize) {
            this.executorService = Executors.newFixedThreadPool(maxPoolSize);
        }

        public void submitHashTask(Path filePath) {
            PendingHashResult pendingHashResult = new PendingHashResult(filePath, executorService.submit(new HashTask(filePath)));
            submittedCount.incrementAndGet();
            sendProgressMessage();
            allTasks.add(pendingHashResult);
        }

        public List<FileState> join() {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS))
                    throw new HashingException("We will never reach this timeout.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            List<FileState> fileStateStream = allTasks.stream().map(this::waitAndGet).collect(Collectors.toList());
            messageConsumer.newLine();
            return fileStateStream;
        }

        private FileState waitAndGet(PendingHashResult pendingHashResult) {
            FileState fileState;
            try {
                fileState = new FileState(pendingHashResult.path, pendingHashResult.hash.get());
            } catch (InterruptedException | ExecutionException e) {
                throw new HashingException("Error while retrieving single hashing result for " + pendingHashResult.path + ": " + e, e);
            }
            return fileState;
        }

        private void sendProgressMessage() {
            int submitted = submittedCount.get();
            int completed = completedCount.get();
            if (submitted > lastSentSubmittedCount || completed > lastSentCompletedCount)
                // only message if positive progress has been made
                messageConsumer.consumeMessageOverride(Message.progressInfo(completed, submitted));
            lastSentSubmittedCount = submittedCount.get();
            lastSentCompletedCount = completedCount.get();
        }

        private class PendingHashResult {
            private final Path path;
            private final Future<byte[]> hash;
            private PendingHashResult(Path path, Future<byte[]> hash) {
                this.path = path;
                this.hash = hash;
            }
        }

        private class HashTask implements Callable<byte[]> {
            private final Path fileToHash;
            private HashTask(Path fileToHash) {
                this.fileToHash = fileToHash;
            }
            @Override
            public byte[] call() {
                byte[] hash = HashUtils.computeFileHash(fileToHash);
                completedCount.incrementAndGet();
                sendProgressMessage();
                return hash;
            }
        }

    }

}

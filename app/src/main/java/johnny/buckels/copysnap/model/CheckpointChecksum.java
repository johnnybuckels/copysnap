package johnny.buckels.copysnap.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

public record CheckpointChecksum(List<Long> checksums) {

    private static final String CHECKSUM_SERDE_DELIMITER = ",";
    private static final CheckpointChecksum UNDEFINED_CHECKSUM = new CheckpointChecksum(List.of(-1L));

    public static CheckpointChecksum undefined() {
        return UNDEFINED_CHECKSUM;
    }

    /**
     * Creates a checksum while transferring the given input stream to the specified output stream.
     */
    public static CheckpointChecksum byTransferring(InputStream is, OutputStream os) {
        List<Long> checksums = new ArrayList<>();
        new CheckpointIterator(is, os)
                .forEachRemaining(checksums::add);
        return new CheckpointChecksum(Collections.unmodifiableList(checksums));
    }

    public static CheckpointChecksum from(InputStream is) {
        List<Long> checksums = new ArrayList<>();
        new CheckpointIterator(is, OutputStream.nullOutputStream())
                .forEachRemaining(checksums::add);
        return new CheckpointChecksum(Collections.unmodifiableList(checksums));
    }

    public CheckpointChecksum {
        if (checksums.isEmpty())
            throw new IllegalArgumentException("Empty checksums");
    }

    /**
     * Expects numbers delimited by {@value CHECKSUM_SERDE_DELIMITER}.
     */
    static CheckpointChecksum deserialize(String checksumString) {
        List<Long> checksums = Arrays.stream(checksumString.split(CHECKSUM_SERDE_DELIMITER))
                .map(String::trim)
                .map(Long::parseLong)
                .toList();
        return new CheckpointChecksum(checksums);
    }

    String serialize() {
        return checksums.stream().map(String::valueOf).collect(Collectors.joining(CHECKSUM_SERDE_DELIMITER));
    }

    /**
     * Implements a "fail fast" check comparing multiple checksums based on checkpoints.
     */
    public boolean hasSameChecksum(InputStream is) {
        if (this.equals(UNDEFINED_CHECKSUM))
            return false;
        CheckpointIterator checkpointIterator = new CheckpointIterator(is, OutputStream.nullOutputStream());
        for (Long expectedChecksum : checksums) {
            if (checkpointIterator.hasNext()) {
                if (!checkpointIterator.next().equals(expectedChecksum))
                    return false;
            } else {
                return false;
            }
        }
        return true;
    }

    private record CheckpointResult(Checksum checksum, long nextCheckpoint, long totalReadBytes, int latestReadByteCount) {}

    /**
     * Not reusable.
     */
    private static class CheckpointIterator implements Iterator<Long> {

        private static final int BASE_BYTE_COUNT = 256;
        private static final int CHECKPOINT_FACTOR = 2;

        private final InputStream is;
        private final OutputStream os;

        // 2^20 bytes: a little more than 1 MB
        private final byte[] buffer = new byte[1048576];

        private CheckpointResult latestCheckpointResult = new CheckpointResult(new CRC32(), BASE_BYTE_COUNT, 0, 0);

        private CheckpointIterator(InputStream is, OutputStream os) {
            this.is = is;
            this.os = os;
        }

        private CheckpointResult getNextCheckpointChecksumValue(InputStream is, CheckpointResult latestCheckpointResult) throws IOException {
            long totalReadByteCount = latestCheckpointResult.totalReadBytes();
            Checksum checksum = latestCheckpointResult.checksum();
            int latestReadByteCount;
            while ((latestReadByteCount = is.read(buffer, 0, getBufferReadBoundary(buffer, latestCheckpointResult.nextCheckpoint(), totalReadByteCount))) > 0) {
                totalReadByteCount += latestReadByteCount;
                checksum.update(buffer, 0, latestReadByteCount);
                os.write(buffer, 0, latestReadByteCount);
                if (totalReadByteCount == latestCheckpointResult.nextCheckpoint()) {
                    // checkpoint is reached
                    return new CheckpointResult(
                            checksum,
                            latestCheckpointResult.nextCheckpoint() * CHECKPOINT_FACTOR,
                            totalReadByteCount,
                            latestReadByteCount);
                } else if (totalReadByteCount > latestCheckpointResult.nextCheckpoint()) {
                    throw new IllegalStateException("Read more bytes than allowed: read=%s, checkpoint=%s".formatted(totalReadByteCount, latestCheckpointResult.nextCheckpoint()));
                }
            }
            // if here, input stream is exhausted: return remaining checksum
            return new CheckpointResult(
                    checksum,
                    latestCheckpointResult.nextCheckpoint() * CHECKPOINT_FACTOR,
                    totalReadByteCount,
                    latestReadByteCount);
        }

        /**
         * The maximum number of bytes to read up until one of the following byte counts is reached:
         * <li>The buffer length</li>
         * <li>Integer max value</li>
         * <li>The difference to the current checkpoint limit: currentCheckpoint - totalReadBytes</li>
         */
        private int getBufferReadBoundary(byte[] buffer, long currentCheckpoint, long totalReadBytes) {
            return Integer.min(buffer.length, (int) Long.min(Integer.MAX_VALUE, currentCheckpoint - totalReadBytes));
        }

        @Override
        public boolean hasNext() {
            return latestCheckpointResult.latestReadByteCount() > -1;
        }

        /**
         * @throws UncheckedIOException If an io exception occurs.
         */
        @Override
        public Long next() {
            try {
                latestCheckpointResult = getNextCheckpointChecksumValue(is, latestCheckpointResult);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not get next checksum: " + e.getMessage(), e);
            }
            return latestCheckpointResult.checksum().getValue();
        }

    }

}

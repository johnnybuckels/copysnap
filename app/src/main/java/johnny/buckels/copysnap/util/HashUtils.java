package johnny.buckels.copysnap.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtils {

    private static final String DIGEST_ALGO = "MD5";
    private static final int BUFFER_SIZE = 512;

    /**
     * @return a hash for the contents of the file at the specified path.
     */
    public static byte[] computeFileHash(Path filePath) {
        MessageDigest md = getNewMessageDigest();
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream is = Files.newInputStream(filePath)) {
            for (int readBytes = is.read(buffer); readBytes > 0; readBytes = is.read(buffer)) {
                md.update(buffer, 0, readBytes);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not compute file hash", e);
        }
        return md.digest();
    }

    public static MessageDigest getNewMessageDigest() {
        try {
            return MessageDigest.getInstance(DIGEST_ALGO);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Unknown MessageDigest algorithm: " + DIGEST_ALGO, e);
        }
    }
}

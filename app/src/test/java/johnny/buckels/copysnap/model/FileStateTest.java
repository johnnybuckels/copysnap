package johnny.buckels.copysnap.model;

import johnny.buckels.copysnap.testutils.FileStateGenerator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileStateTest {

    private final FileStateGenerator fileStateGenerator = new FileStateGenerator();

    @Test
    public void serde() {
        // byte is int from -128 to 127 = 255 positions
        FileState fs = fileStateGenerator.generateRandomFileState();
        System.out.println("Input: " + fs);

        String s = fs.toStringRepresentation();
        System.out.println("String representation: " + s);

        FileState fsParsed = FileState.parse(s);

        assertEquals(fs, fsParsed);
    }

    @Test
    public void serde_withEmptyHash() {
        // byte is int from -128 to 127 = 255 positions
        FileState fs = new FileState(Path.of("a/b/c"), new byte[]{});
        System.out.println("Input: " + fs);

        String s = fs.toStringRepresentation();
        System.out.println("String representation: " + s);

        FileState fsParsed = FileState.parse(s);

        assertEquals(fs, fsParsed);
    }

}

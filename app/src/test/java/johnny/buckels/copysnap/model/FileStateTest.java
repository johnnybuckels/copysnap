package johnny.buckels.copysnap.model;

import johnny.buckels.copysnap.testutils.FileStateGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileStateTest {

    private final FileStateGenerator fileStateGenerator = new FileStateGenerator();

    @Test
    public void testSerDe() {
        // byte is int from -128 to 127 = 255 positions
        FileState fs = fileStateGenerator.generateRandomFileState();
        System.out.println("Input: " + fs);

        String s = fs.toStringRepresentation();
        System.out.println("String representation: " + s);

        FileState fsParsed = FileState.parse(s);

        assertEquals(fs, fsParsed);
    }

}

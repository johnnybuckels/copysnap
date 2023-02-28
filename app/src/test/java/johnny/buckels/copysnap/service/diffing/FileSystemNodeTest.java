package johnny.buckels.copysnap.service.diffing;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class FileSystemNodeTest {

    @Test
    public void testFileSystemNode_appendSingleName() {
        // given
        FileSystemNode root = FileSystemNode.createNew();
        // when
        Path ra = Path.of("r", "a");
        FileSystemNode aNode = root.append(ra);

        // then
        assertEquals(ra, aNode.getValue());
    }

    /**
     * Given: (r)
     * Append: a/b/c at (r)
     * Expect: (r)
     *           \-(r/a)-(r/a/b)-(r/a/b/c)
     */
    @Test
    public void testFileSystemNode_appendPath() {
        // given
        FileSystemNode root = FileSystemNode.createNew();
        Path rabc = Path.of("r", "a", "b", "c");

        // when
        FileSystemNode cNode = root.append(rabc);

        // then
        FileSystemNode bNode = cNode.getParent();
        FileSystemNode aNode = bNode.getParent();
        assertEquals(rabc, cNode.getValue());
        assertEquals(Path.of("r", "a", "b"), bNode.getValue());
        assertEquals(Path.of("r", "a"), aNode.getValue());
    }

    @Test
    public void testFileSystemNode_insert() {
        // given
        FileSystemNode root = FileSystemNode.createNew();
        Path r = Path.of("r");
        Path a = Path.of("r", "a");
        Path b = Path.of("r", "b");
        Path c = Path.of("r", "c");
        Path ca = Path.of("r", "c", "ca");

        // when
        FileSystemNode rNode = root.insert(r);
        FileSystemNode aNode = root.insert(a);
        FileSystemNode bNode = root.insert(b);
        FileSystemNode cNode = root.insert(c);
        FileSystemNode caNode = root.insert(ca);

        // then
        Set<FileSystemNode> childrenRoot = root.getChildren();
        Set<FileSystemNode> childrenR = rNode.getChildren();
        assertEquals(Set.of(rNode), childrenRoot);
        assertEquals(Set.of(aNode, bNode, cNode), childrenR);
        assertEquals(c, cNode.getValue());
        assertEquals(ca, caNode.getValue());
    }

    /**
     * Given: (r)-(r/a)-(r/a/b)-(r/a/b/c)
     * Inserting: r/a/b/c/f at any node along the "branch"
     * Expect: the same node being created every time
     *        (r)-(r/a)-(r/a/b)-(r/a/b/c)-(r/a/b/c/f)
     *
     */
    @Test
    public void insertingPathAlongMainPath_expectSameResults() {
        // given
        Path rabc = Path.of("r", "a", "b", "c");
        Supplier<FileSystemNode> newTreeSupplier = () -> {
            FileSystemNode root = FileSystemNode.createNew();
            root.insert(rabc);
            return root;
        };

        FileSystemNode r1 = newTreeSupplier.get();
        FileSystemNode r2 = newTreeSupplier.get();
        FileSystemNode r2aNode = new ArrayList<>(r2.getChildren()).get(0);
        FileSystemNode r3 = newTreeSupplier.get();
        FileSystemNode r3cNode = new ArrayList<>(r3.getLeafs()).get(0);

        Path rabcdf = rabc.resolve("f");

        // when
        FileSystemNode r1Insert = r1.insert(rabcdf);
        FileSystemNode r2aNodeInnsert = r2aNode.insert(rabcdf);
        FileSystemNode r3cNodeInsert = r3cNode.insert(rabcdf);

        // then
        assertEquals(rabcdf, r1Insert.getValue());
        assertEquals(rabcdf, r2aNodeInnsert.getValue());
        assertEquals(rabcdf, r3cNodeInsert.getValue());
    }

    @Test
    public void insertSubpath_expectNoNewInsertion() {
        // given
        FileSystemNode root = FileSystemNode.createNew();
        Path f = Path.of("r", "a", "b", "c", "d", "e", "f");
        Path c = Path.of("r", "a", "b", "c");

        // when
        FileSystemNode fNode = root.insert(f);
        FileSystemNode cNode = root.insert(c);

        // then
        assertEquals(c, cNode.getValue());
        assertEquals(Set.of(fNode), root.getLeafs());
    }

    /**
     * Given: (r)
     *          \-(r/a)
     *          \-(r/b)
     *          \-(r/c)-(r/c/d)
     * Insert: r/c/x/y/z into (r)
     * Expect: (r)
     *           \-(r/a)
     *           \-(r/b)
     *           \-(r/c)-(r/c/d)
     *                 \-(r/c/x)-(r/c/x/y)-(r/c/x/y/z)
     */
    @Test
    public void testFileSystemNode_insert2() {
        // given
        FileSystemNode root = FileSystemNode.createNew();
        root.insert(Path.of("r", "a"));
        root.insert(Path.of("r", "b"));
        FileSystemNode cNode = root.insert(Path.of("r", "c"));
        FileSystemNode dNode = root.insert(Path.of("r", "c", "d"));

        // when
        Path rcxyz = Path.of("r", "c", "x", "y", "z");
        FileSystemNode zNode = root.insert(rcxyz);

        // then
        FileSystemNode yNode = zNode.getParent();
        FileSystemNode xNode = yNode.getParent();
        assertEquals(rcxyz, zNode.getValue());
        assertEquals(Path.of("r", "c", "x", "y"), yNode.getValue());
        assertEquals(Path.of("r", "c", "x"), xNode.getValue());
        assertEquals(cNode, xNode.getParent());
        assertEquals(Set.of(dNode, xNode), cNode.getChildren());
    }

    @Test
    public void testFileSystemNode_insertMultiplePaths() {
        // given
        FileSystemNode root = FileSystemNode.createNew();

        // when
        Path rafP = Path.of("r/a/f");
        FileSystemNode raf = root.insert(rafP);
        Path rbfP = Path.of("r/b/f");
        FileSystemNode rbf = root.insert(rbfP);
        Path raaafP = Path.of("r/a/aa/f");
        FileSystemNode raaaf = root.insert(raaafP);
        Path rccafP = Path.of("r/c/ca/f");
        FileSystemNode rccaf = root.insert(rccafP);
        Path rccbfP = Path.of("r/c/cb/f");
        FileSystemNode rccbf = root.insert(rccbfP);
        Path rP = Path.of("r");
        FileSystemNode r = root.insert(rP);

        // then
        assertEquals(rafP, raf.getValue());
        assertEquals(rbfP, rbf.getValue());
        assertEquals(raaafP, raaaf.getValue());
        assertEquals(rccafP, rccaf.getValue());
        assertEquals(rccbfP, rccbf.getValue());
        assertEquals(rP, r.getValue());
        assertEquals(Set.of(r), root.getChildren());
        assertEquals(3, r.getChildren().size()); // should be r/a, r/b, r/c
    }

    @Test
    public void testFileSystemNode_changed() {
        // given
        FileSystemNode root = FileSystemNode.createNew()
                .append(Path.of("r"));
        Path a = Path.of("a");
        Path b = Path.of("b");
        Path c = Path.of("c");
        Path ca = Path.of("ca");
        FileSystemNode aNode = root.append(a);
        FileSystemNode bNode = root.append(b);
        FileSystemNode cNode = root.append(c);
        FileSystemNode caNode = cNode.append(ca);

        // when
        caNode.markAsChanged();

        // then
        assertTrue(root.isChanged());
        assertTrue(cNode.isChanged());
        assertTrue(caNode.isChanged());
        assertFalse(aNode.isChanged());
        assertFalse(bNode.isChanged());
    }

    @Test
    public void testFileSystemNode_deepestKnown() {
        // given
        FileSystemNode root = FileSystemNode.createNew();
        FileSystemNode rNode = root.append(Path.of("r"));
        Path a = Path.of("a");
        Path b = Path.of("b");
        Path c = Path.of("c");
        Path ca = Path.of("ca");
        FileSystemNode aNode = root.append(a);
        root.append(b);
        FileSystemNode cNode = root.append(c);
        FileSystemNode caNode = cNode.append(ca);

        // when
        FileSystemNode deepestKnownCa = root.getDeepestKnownAlong(caNode.getValue());
        FileSystemNode deepestKnownC = root.getDeepestKnownAlong(cNode.getValue());
        FileSystemNode deepestKnownR = root.getDeepestKnownAlong(rNode.getValue());
        FileSystemNode deepestKnownEmpty = root.getDeepestKnownAlong(Path.of(""));
        FileSystemNode unknown = root.getDeepestKnownAlong(Path.of("some/unknown/path"));
        FileSystemNode unknownAfterA = root.getDeepestKnownAlong(aNode.getValue().resolve("some/deeper/path"));

        // then
        assertEquals(caNode, deepestKnownCa);
        assertEquals(cNode, deepestKnownC);
        assertEquals(rNode, deepestKnownR);
        assertEquals(root, deepestKnownEmpty);
        assertEquals(root, unknown);
        assertEquals(aNode, unknownAfterA);
    }

    @Test
    public void testFileSystemNode_uppermostUnchanged() {
        // given
        FileSystemNode root = FileSystemNode.createNew()
                .append(Path.of("r"));
        Path a = Path.of("a");
        Path aa = Path.of("aa");
        Path b = Path.of("b");
        Path c = Path.of("c");
        Path ca = Path.of("ca");
        FileSystemNode aNode = root.append(a);
        FileSystemNode aaNode = aNode.append(aa);
        FileSystemNode bNode = root.append(b);
        FileSystemNode cNode = root.append(c);
        FileSystemNode caNode = cNode.append(ca);

        // when
        caNode.markAsChanged();

        // then
        assertEquals(root, caNode.getUppermostUnchanged());
        assertEquals(bNode, bNode.getUppermostUnchanged());
        assertEquals(aNode, aaNode.getUppermostUnchanged());
    }

    @Test
    public void testFileSystemNode_uppermostUnchanged_whenNothingChanged() {
        // given
        FileSystemNode root = FileSystemNode.createNew();
        Path rabcf = Path.of("r", "a", "b", "c", "f");
        FileSystemNode fNode = root.insert(rabcf);

        FileSystemNode rNode = new ArrayList<>(root.getChildren()).get(0);

        // when
        FileSystemNode actualUppermostUnchanged = fNode.getUppermostUnchanged();

        // then
        assertEquals(rNode, actualUppermostUnchanged);
    }

    /**
     * Given: (r)
     *           \-(r/a)
     *           \-(r/b)
     *           \-(r/c)-(r/c/d)
     *                 \-(r/c/x)-(r/c/x/y)-(r/c/x/y/z)
     */
    @Test
    public void testFileSystemNode_leafs_empty() {
        // given
        FileSystemNode root = FileSystemNode.createNew();
        FileSystemNode aNode = root.insert(Path.of("r", "a"));
        FileSystemNode bNode = root.insert(Path.of("r", "b"));
        FileSystemNode dNode = root.insert(Path.of("r", "c", "d"));
        FileSystemNode zNode = root.insert(Path.of("r", "c", "x", "y", "z"));

        // when
        Set<FileSystemNode> leafs = root.getLeafs();

        // then
        assertEquals(Set.of(aNode, bNode, dNode, zNode), leafs);
    }

}

package johnny.buckels.copysnap.service.diffing;

import java.nio.file.Path;
import java.util.*;

public class FileSystemNode {

    private static final Path ROOT_PATH = Path.of("");
    
    private final Path value;
    private final FileSystemNode parent;
    private final Map<Path, FileSystemNode> children = new HashMap<>();

    private boolean changed = false;

    public static FileSystemNode getNew() {
        return new FileSystemNode();
    }

    FileSystemNode() {
        this(FileSystemNode.ROOT_PATH, null);
    }

    FileSystemNode(Path value, FileSystemNode parent) {
        this.value = value;
        this.parent = parent;
    }

    /**
     * Example:
     * This: (r)
     *         \-(r/a)
     *         \-(r/b)
     *         \-(r/c)-(r/c/d)
     * Path: r/c/x/y/z
     * Result: (r)
     *           \-(r/a)
     *           \-(r/b)
     *           \-(r/c)-(r/c/d)
     *                 \-(r/c/x)-(r/c/x/y)-(r/c/x/y/z)
     * Returns: (r/c/x/y/z)
     */
    public FileSystemNode insert(Path relPath) {
        if (relPath.isAbsolute())
            throw new IllegalArgumentException("Can not insert absolute path");
        if (relPath.equals(value))
            return this;
        Path relFromThisToPath = value.relativize(relPath);
        Path pathToChild = value.resolve(relFromThisToPath.getName(0));
        FileSystemNode matchingChild = children.get(pathToChild);
        if (matchingChild == null)
            return append(relFromThisToPath);
        else
            return matchingChild.insert(relPath);
    }

    public FileSystemNode getDeepestKnownAlong(Path relPath) {
        if (relPath.isAbsolute())
            throw new IllegalArgumentException("Can not process absolute path");
        if (relPath.equals(value)) {
            return this;
        }
        Path relFromThisToPath = value.relativize(relPath);
        Path pathToChild = value.resolve(relFromThisToPath.getName(0));
        FileSystemNode matchingChild = children.get(pathToChild);
        if (matchingChild == null)
            return this;
        else
            return matchingChild.getDeepestKnownAlong(relPath);
    }

    /**
     * This: (r)
     * Path: a/b/c
     * This afterwards: (r)
     *                    \-(r/a)-(r/a/b)-(r/a/b/c)
     * Returns: (r/a/b/c)
     */
    protected FileSystemNode append(Path path) {
        if (path.isAbsolute())
            throw new IllegalArgumentException("Can not append absolute path: " + path);
        if (path.getNameCount() == 0)
            return this;
        Path nextChildPath = value;
        Iterator<Path> pathElemIt = path.iterator();
        FileSystemNode currentParent = this;
        FileSystemNode nextChild;
        while (pathElemIt.hasNext()) {
            nextChildPath = nextChildPath.resolve(pathElemIt.next());
            nextChild = new FileSystemNode(nextChildPath, currentParent);
            currentParent.children.put(nextChild.value, nextChild);
            currentParent = nextChild;
        }
        return currentParent;
    }

    public void markAsChanged() {
        this.changed = true;
        FileSystemNode parent = this.parent;
        while(parent != null) {
            parent.changed = true;
            parent = parent.parent;
        }
    }

    public FileSystemNode getUppermostUnchanged() {
        FileSystemNode current = this;
        FileSystemNode parent = this.parent;
        while (!parent.isRoot()) {
            if (parent.isChanged() && current.isUnchanged())
                return current;
            current = parent;
            parent = parent.parent;
        }
        return current;
    }

    public boolean isRoot() {
        return ROOT_PATH.equals(value);
    }

    public boolean isChanged() {
        return changed;
    }

    public boolean isUnchanged() {
        return !isChanged();
    }

    public boolean isLeaf() {
        return children.size() == 0;
    }

    public Path getValue() {
        return value;
    }

    public FileSystemNode getParent() {
        return parent;
    }

    public Set<FileSystemNode> getChildren() {
        return new HashSet<>(children.values());
    }

    public Set<FileSystemNode> getLeafs() {
        Set<FileSystemNode> leafs = new HashSet<>();
        for (FileSystemNode child : children.values())
            if (child.isLeaf())
                leafs.add(child);
            else
                leafs.addAll(child.getLeafs());
        return leafs;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }



}

package johnny.buckels.copysnap.model;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Properties;

public class ContextProperties {

    private static final String SOURCE_DIR_KEY = "sourceDir";
    private static final String SNAPSHOTS_HOME_DIR_KEY = "snapshotsHomeDir";
    private static final String CREATED_KEY = "created";
    private static final String LAST_MODIFIED_KEY = "lastModified";
    private static final String REFERENCE_SNAPSHOT_KEY = "referenceSnapshot";

    private final Root sourceRoot;
    private final Path snapshotsHomeDir;
    private final ZonedDateTime created;
    private final ZonedDateTime lastModified; // nullable
    /**
     * The snapshot to compare to when creating new snapshots.
     */
    private final Path referenceSnapshot; // nullable

    private static final String PROPERTIES_EXCEPTION_TEMPLATE = "Could not find field %s in properties.";

    public static ContextProperties getNew(Path sourceDir, Path snapshotsHomeDir) {
        ZonedDateTime now = ZonedDateTime.now();
        return new ContextProperties(Root.from(sourceDir), snapshotsHomeDir, now, now, null);
    }

    public static ContextProperties readFrom(Properties properties) throws IllegalPropertiesException {
        Path sourceDir = Optional.ofNullable(properties.getProperty(SOURCE_DIR_KEY)).map(Path::of)
                .orElseThrow(() -> new IllegalPropertiesException(String.format(PROPERTIES_EXCEPTION_TEMPLATE, SOURCE_DIR_KEY)));
        Path snapshotsHomeDir = Optional.ofNullable(properties.getProperty(SNAPSHOTS_HOME_DIR_KEY)).map(Path::of)
                .orElseThrow(() -> new IllegalPropertiesException(String.format(PROPERTIES_EXCEPTION_TEMPLATE, SNAPSHOTS_HOME_DIR_KEY)));
        ZonedDateTime created = Optional.ofNullable(properties.getProperty(CREATED_KEY)).map(s -> ZonedDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .orElseThrow(() -> new IllegalPropertiesException(String.format(PROPERTIES_EXCEPTION_TEMPLATE, CREATED_KEY)));
        ZonedDateTime lastModified = Optional.ofNullable(properties.getProperty(LAST_MODIFIED_KEY)).map(s -> ZonedDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .orElse(null);
        Path referenceSnapshot = Optional.ofNullable(properties.getProperty(REFERENCE_SNAPSHOT_KEY)).map(Path::of).map(Path::toAbsolutePath)
                .orElse(null);
        return new ContextProperties(Root.from(sourceDir), snapshotsHomeDir, created, lastModified, referenceSnapshot);
    }

    /**
     * Only tries to read {@value SNAPSHOTS_HOME_DIR_KEY} and {@value SOURCE_DIR_KEY} from the specified properties.
     * Other values are reinitialized.
     * @throws IllegalArgumentException if either {@value SNAPSHOTS_HOME_DIR_KEY} or {@value SOURCE_DIR_KEY} are not
     * specified in the specified properties.
     */
    public static ContextProperties readFromShallow(Properties properties) throws IllegalArgumentException {
        Path sourceDir = Optional.ofNullable(properties.getProperty(SOURCE_DIR_KEY)).map(Path::of)
                .orElseThrow(() -> new IllegalArgumentException(String.format(PROPERTIES_EXCEPTION_TEMPLATE, SOURCE_DIR_KEY)));
        Path snapshotsHomeDir = Optional.ofNullable(properties.getProperty(SNAPSHOTS_HOME_DIR_KEY)).map(Path::of)
                .orElseThrow(() -> new IllegalArgumentException(String.format(PROPERTIES_EXCEPTION_TEMPLATE, SNAPSHOTS_HOME_DIR_KEY)));
        return new ContextProperties(Root.from(sourceDir), snapshotsHomeDir, ZonedDateTime.now(), null, null);
    }

    private ContextProperties(Root sourceRoot, Path snapshotsHomeDir, ZonedDateTime created, ZonedDateTime lastModified, Path referenceSnapshot) {
        this.sourceRoot = sourceRoot;
        this.snapshotsHomeDir = snapshotsHomeDir;
        this.created = created;
        this.lastModified = lastModified;
        this.referenceSnapshot = referenceSnapshot;
    }

    public ContextProperties getNewUpdated(Path newReferenceSnapshot) {
        return new ContextProperties(sourceRoot, snapshotsHomeDir, created, ZonedDateTime.now(), newReferenceSnapshot);
    }

    /**
     * Writes the contents of this to the specified destination.
     */
    public void write(Path destination) throws IOException {
        Properties properties = new Properties();
        properties.put(SOURCE_DIR_KEY, sourceRoot.pathToRootDir().toString());
        properties.put(SNAPSHOTS_HOME_DIR_KEY, snapshotsHomeDir.toString());
        properties.put(CREATED_KEY, created.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        if (lastModified != null)
            properties.put(LAST_MODIFIED_KEY, lastModified.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        if (referenceSnapshot != null)
            properties.put(REFERENCE_SNAPSHOT_KEY, referenceSnapshot.toString());

        try (BufferedWriter bw = Files.newBufferedWriter(destination, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)){
            properties.store(bw, "CopySnap context properties");
        }
    }

    public Root getSourceRoot() {
        return sourceRoot;
    }

    public Path getSnapshotsHomeDir() {
        return snapshotsHomeDir;
    }

    public Optional<ZonedDateTime> getLastModified() {
        return Optional.ofNullable(lastModified);
    }

    public Optional<Path> getReferenceSnapshot() {
        return Optional.ofNullable(referenceSnapshot);
    }

    public String toDisplayString() {
        return String.format("%24s: %s", "source", sourceRoot) +
                "\n" + String.format("%24s: %s", "home", snapshotsHomeDir) +
                "\n" + String.format("%24s: %s", "created", created.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)) +
                "\n" + String.format("%24s: %s", "last modified", getLastModified().map(t -> t.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).orElse("")) +
                "\n" + String.format("%24s: %s", "reference snapshot", getReferenceSnapshot().map(Path::toString).orElse(""));
    }

}

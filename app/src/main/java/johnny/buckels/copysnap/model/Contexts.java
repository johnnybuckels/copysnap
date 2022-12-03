package johnny.buckels.copysnap.model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

public class Contexts {

    private static final String COPYSNAP_HOME_DIR_POSTFIX = "copysnap";
    private static final String CONTEXT_PROPERTIES_FILE_NAME = "context.properties";

    private static final String SOURCE_DIR_KEY = "sourceDir";
    private static final String SNAPSHOTS_HOME_DIR_KEY = "snapshotsHomeDir";
    private static final String CREATED_KEY = "created";
    private static final String LAST_SNAPSHOT_KEY = "lastSnapshot";

    private static final Set<String> PROPERTY_KEYS = Set.of(SOURCE_DIR_KEY, SNAPSHOTS_HOME_DIR_KEY, CREATED_KEY, LAST_SNAPSHOT_KEY);

    private Contexts() {
        // do not instantiate
    }

    /**
     * @param sourceDir the directory to take snapshots from.
     * @param snapshotsHomeDirLocation the directory where the new context home directory should be created in.
     */
    public static Context createNew(Path sourceDir, Path snapshotsHomeDirLocation) {
        Path snapshotsHomeDir = snapshotsHomeDirLocation.resolve(sourceDir.getFileName().toString() + "-" + COPYSNAP_HOME_DIR_POSTFIX);
        ZonedDateTime created = ZonedDateTime.now();
        if (Files.isDirectory(snapshotsHomeDir))
            throw new IllegalStateException("Context already exists: " + snapshotsHomeDir);
        return new Context(sourceDir, snapshotsHomeDir, created, null);
    }

    /**
     * Tries to load properties from the specified path. Either it points to the properties file or
     * to a directory directly containing the properties file at depth 1.
     */
    public static Context findPropertiesAndLoadContext(Path path) {
        if (Files.isRegularFile(path)) {
            return loadFromPropertiesFile(path);
        } else if (Files.isDirectory(path)) {
            try (Stream<Path> pathStream = Files.find(path, 1, (p, bfa) -> bfa.isRegularFile() && p.getFileName().toString().equals(CONTEXT_PROPERTIES_FILE_NAME))) {
                Path propertiesPath = pathStream.findFirst().orElseThrow(() -> new IllegalArgumentException("Could not find context properties in " + path));
                return loadFromPropertiesFile(propertiesPath);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not find context properties in " + path, e);
            }
        }
        throw new IllegalArgumentException("Not a file or directory: " + path);
    }

    /**
     * @return The path to the saved context properties files.
     */
    public static Path saveContextProperties(Context context) {
        Properties properties = new Properties();
        properties.put(SOURCE_DIR_KEY, context.getSourceDir().toString());
        properties.put(SNAPSHOTS_HOME_DIR_KEY, context.getHomeDir().toString());
        properties.put(CREATED_KEY, context.getCreated().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        properties.put(LAST_SNAPSHOT_KEY, context.getLastSnapshot() == null ? "" : context.getLastSnapshot().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        Path propertiesFile = context.getHomeDir().resolve(CONTEXT_PROPERTIES_FILE_NAME);
        try {
            Files.createDirectories(context.getHomeDir());
            try (BufferedWriter bw = Files.newBufferedWriter(propertiesFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)){
                properties.store(bw, "CopySnap properties");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save CopySnap home directory: " + context.getHomeDir(), e);
        }
        return propertiesFile;
    }

    private static Context loadFromPropertiesFile(Path path) {
        Properties properties = new Properties();
        try (BufferedReader br = Files.newBufferedReader(path)) {
            properties.load(br);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load properties from " + path, e);
        }
        return fromProperties(properties);
    }

    private static Context fromProperties(Properties properties) {
        validateProperties(properties);
        Path sourceDir = Path.of(Objects.requireNonNull(properties.getProperty(SOURCE_DIR_KEY)));
        Path snapshotsHomeDir = Path.of(Objects.requireNonNull(properties.getProperty(SNAPSHOTS_HOME_DIR_KEY)));
        ZonedDateTime created = ZonedDateTime.parse(Objects.requireNonNull(properties.getProperty(CREATED_KEY)), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String lastSnapshotStr = properties.getProperty(LAST_SNAPSHOT_KEY);
        ZonedDateTime lastSnapshot = lastSnapshotStr == null || lastSnapshotStr.isBlank() ? null : ZonedDateTime.parse(lastSnapshotStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return new Context(sourceDir, snapshotsHomeDir, created, lastSnapshot);
    }

    private static void validateProperties(Properties properties) {
        if (!properties.keySet().equals(PROPERTY_KEYS))
            throw new IllegalArgumentException("Properties do not contain expected keys " + PROPERTY_KEYS + ": " + properties.keySet());
    }

}

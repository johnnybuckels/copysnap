package johnny.buckels.copysnap.model;

import java.io.OutputStream;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Properties;

record ContextProperties(Root source, Path snapshotsHomeDir, ZonedDateTime created, SnapshotProperties snapshotProperties) {

    private static final String SOURCE_DIR_KEY = "sourceDir";
    private static final String SNAPSHOTS_HOME_DIR_KEY = "snapshotsHomeDir";
    private static final String CREATED_KEY = "created";

    static ContextProperties getNew(Path sourceDir, Path snapshotsHomeDir) {
        return new ContextProperties(Root.from(sourceDir), snapshotsHomeDir, ZonedDateTime.now(), SnapshotProperties.EMPTY);
    }

    static ContextProperties fromProperties(Properties properties) {
        Path sourceDir = Optional.ofNullable(properties.getProperty(SOURCE_DIR_KEY))
                .map(Path::of)
                .orElseThrow(() -> illegalPropertiesException(properties, SOURCE_DIR_KEY));
        Path snapshotsHomeDir = Optional.ofNullable(properties.getProperty(SNAPSHOTS_HOME_DIR_KEY))
                .map(Path::of)
                .orElseThrow(() -> illegalPropertiesException(properties, SNAPSHOTS_HOME_DIR_KEY));
        // TODO: Time Parser to utils separate package
        ZonedDateTime created = Optional.ofNullable(properties.getProperty(CREATED_KEY))
                .map(s -> ZonedDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .orElseThrow(() -> illegalPropertiesException(properties, CREATED_KEY));
        SnapshotProperties snapshotProperties = SnapshotProperties.fromProperties(properties);
        return new ContextProperties(Root.from(sourceDir), snapshotsHomeDir, created, snapshotProperties);
    }

    private static IllegalPropertiesException illegalPropertiesException(Properties properties, String key) {
        return new IllegalPropertiesException("Key %s not present in %s".formatted(key, properties.keySet()));
    }

    Properties toProperties() {
        Properties properties = new Properties();
        properties.put(SOURCE_DIR_KEY, source.pathToRootDir().toString());
        properties.put(SNAPSHOTS_HOME_DIR_KEY, snapshotsHomeDir.toString());
        properties.put(CREATED_KEY, created.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        properties.putAll(snapshotProperties.toProperties());
        return properties;
    }

    String toDisplayString() {
        return """
                source  %s
                home    %s
                created %s
                """.formatted(source, snapshotsHomeDir, created.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                + snapshotProperties.toDisplayString();
    }

    public ContextProperties withSnapshotProperties(SnapshotProperties snapshotProperties) {
        return new ContextProperties(source, snapshotsHomeDir, created, snapshotProperties);
    }

    record SnapshotProperties(Path rootLocation, ZonedDateTime created, int fileCount) {

        private static final String ROOT_LOCATION_KEY = "rootLocation";
        private static final String CREATED_KEY = "created";
        private static final String FILE_COUNT_KEY = "fileCount";

        private static final SnapshotProperties EMPTY = new SnapshotProperties(null, null, -1);

        private Properties toProperties() {
            Properties properties = new Properties();
            if (this.equals(EMPTY)) {
                return properties;
            }
            properties.put(ROOT_LOCATION_KEY, rootLocation);
            properties.put(CREATED_KEY, created);
            properties.put(FILE_COUNT_KEY, rootLocation);
            return properties;
        }

        private static SnapshotProperties fromProperties(Properties properties) throws IllegalPropertiesException {
            Path rootLocation = Optional.ofNullable(properties.getProperty(ROOT_LOCATION_KEY))
                    .map(Path::of)
                    .orElseThrow(() -> illegalPropertiesException(properties, ROOT_LOCATION_KEY));
            ZonedDateTime created = Optional.ofNullable(properties.getProperty(CREATED_KEY))
                    // TODO: Time Parser to utils separate package
                    .map(ZonedDateTime::parse)
                    .orElseThrow(() -> illegalPropertiesException(properties, CREATED_KEY));
            int fileCount = Optional.ofNullable(properties.getProperty(FILE_COUNT_KEY))
                    .map(Integer::parseInt)
                    .orElseThrow(() -> illegalPropertiesException(properties, FILE_COUNT_KEY));
            return new SnapshotProperties(rootLocation, created, fileCount);
        }

        private static IllegalPropertiesException illegalPropertiesException(Properties properties, String key) {
            return new IllegalPropertiesException("Key %s not present in %s".formatted(key, properties.keySet()));
        }

        private String toDisplayString() {
            if (this.equals(EMPTY)) {
                return "";
            }
            return """
               latest snapshot
                   location   %s
                   created    %s
                   file count %s
               """.formatted(rootLocation, created, fileCount);
        }

    }
}

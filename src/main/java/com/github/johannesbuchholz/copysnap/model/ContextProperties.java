package com.github.johannesbuchholz.copysnap.model;

import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Properties;

record ContextProperties(Root source, Path snapshotsHomeDir, Instant created, SnapshotProperties snapshotProperties) {

    private static final String SOURCE_DIR_KEY = "sourceDir";
    private static final String SNAPSHOTS_HOME_DIR_KEY = "snapshotsHomeDir";
    private static final String CREATED_KEY = "created";

    static ContextProperties getNew(Path sourceDir, Path snapshotsHomeDir) {
        return new ContextProperties(Root.from(sourceDir), snapshotsHomeDir, Instant.now(), null);
    }

    static ContextProperties fromProperties(Properties properties) {
        Root source = Optional.ofNullable(properties.getProperty(SOURCE_DIR_KEY))
                .map(Path::of)
                .map(Root::from)
                .orElseThrow(() -> illegalPropertiesException(properties, SOURCE_DIR_KEY));
        Path snapshotsHomeDir = Optional.ofNullable(properties.getProperty(SNAPSHOTS_HOME_DIR_KEY))
                .map(Path::of)
                .orElseThrow(() -> illegalPropertiesException(properties, SNAPSHOTS_HOME_DIR_KEY));
        Instant created = Optional.ofNullable(properties.getProperty(CREATED_KEY))
                .map(TimeUtils::fromString)
                .orElseThrow(() -> illegalPropertiesException(properties, CREATED_KEY));
        SnapshotProperties snapshotProperties = SnapshotProperties.fromProperties(properties);
        return new ContextProperties(source, snapshotsHomeDir, created, snapshotProperties);
    }

    private static IllegalPropertiesException illegalPropertiesException(Properties properties, String key) {
        return new IllegalPropertiesException("Key %s not present in %s".formatted(key, properties.keySet()));
    }

    Properties toProperties() {
        Properties properties = new Properties();
        properties.put(SOURCE_DIR_KEY, source.pathToRootDir().toString());
        properties.put(SNAPSHOTS_HOME_DIR_KEY, snapshotsHomeDir.toString());
        properties.put(CREATED_KEY, TimeUtils.asString(created));
        if(snapshotProperties != null) {
            properties.putAll(snapshotProperties.toProperties());
        }
        return properties;
    }

    String toDisplayString() {
        return """
                source : %s
                home   : %s
                created: %s
                latest snapshot
                %s""".formatted(source, snapshotsHomeDir, TimeUtils.asString(created),
                snapshotProperties == null ? "none".indent(4).stripTrailing() : snapshotProperties.toDisplayString().indent(4).stripTrailing());
    }

    public ContextProperties withSnapshotProperties(SnapshotProperties snapshotProperties) {
        return new ContextProperties(source, snapshotsHomeDir, created, snapshotProperties);
    }

    record SnapshotProperties(Path rootLocation, Instant created, int fileCount) {

        private static final String ROOT_LOCATION_KEY = "latestSnapshotRootLocation";
        private static final String CREATED_KEY = "latestSnapshotCreated";
        private static final String FILE_COUNT_KEY = "latestSnapshotFileCount";

        static final SnapshotProperties EMPTY = new ContextProperties.SnapshotProperties(null, Instant.now(), -1);

        private Properties toProperties() {
            Properties properties = new Properties();
            properties.put(ROOT_LOCATION_KEY, rootLocation.toString());
            properties.put(CREATED_KEY, TimeUtils.asString(created));
            properties.put(FILE_COUNT_KEY, String.valueOf(fileCount));
            return properties;
        }

        private static SnapshotProperties fromProperties(Properties properties) throws IllegalPropertiesException {
            if (!properties.containsKey(ROOT_LOCATION_KEY)) {
                return null;
            }
            Path rootLocation = Optional.ofNullable(properties.getProperty(ROOT_LOCATION_KEY))
                    .map(Path::of)
                    .orElseThrow(() -> illegalPropertiesException(properties, ROOT_LOCATION_KEY));
            Instant created = Optional.ofNullable(properties.getProperty(CREATED_KEY))
                    .map(TimeUtils::fromString)
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
            return """
               location  : %s
               created   : %s
               file count: %s""".formatted(rootLocation, created, fileCount);
        }

    }

    private static class TimeUtils {

        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

        static String asString(Instant time) {
            return time.atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.MINUTES).format(FORMATTER);
        }

        static Instant fromString(String timeString) {
            return Instant.from(FORMATTER.parse(timeString));
        }

    }

}

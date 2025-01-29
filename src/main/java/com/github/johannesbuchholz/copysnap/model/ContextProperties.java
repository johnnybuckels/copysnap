package com.github.johannesbuchholz.copysnap.model;

import com.github.johannesbuchholz.copysnap.util.TimeUtils;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

record ContextProperties(
        Root source,
        Path snapshotsHomeDir,
        ZonedDateTime created,
        List<String> ignorePathGlobPatterns,
        /* nullable */
        SnapshotProperties snapshotProperties
) {

    static final String SOURCE_DIR_KEY = "sourceDir";
    private static final String SNAPSHOTS_HOME_DIR_KEY = "snapshotsHomeDir";
    private static final String CREATED_KEY = "created";
    private static final String IGNORE_KEY = "ignore";
    private static final String IGNORE_PATTERN_DELIMITER = ":";

    static ContextProperties getNew(Path sourceDir, Path snapshotsHomeDir, String... ignorePatterns) {
        return new ContextProperties(Root.from(sourceDir), snapshotsHomeDir, ZonedDateTime.now(), List.of(ignorePatterns), null);
    }

    static ContextProperties fromProperties(Properties properties) {
        Root source = Optional.ofNullable(properties.getProperty(SOURCE_DIR_KEY))
                .map(Path::of)
                .map(Root::from)
                .orElseThrow(() -> illegalPropertiesException(properties, SOURCE_DIR_KEY));
        Path snapshotsHomeDir = Optional.ofNullable(properties.getProperty(SNAPSHOTS_HOME_DIR_KEY))
                .map(Path::of)
                .orElseThrow(() -> illegalPropertiesException(properties, SNAPSHOTS_HOME_DIR_KEY));
        ZonedDateTime created = Optional.ofNullable(properties.getProperty(CREATED_KEY))
                .map(TimeUtils::fromString)
                .orElseThrow(() -> illegalPropertiesException(properties, CREATED_KEY));
        List<String> excludedSubPaths = Optional.ofNullable(properties.getProperty(IGNORE_KEY))
                .map(excludedPathString -> Arrays.stream(excludedPathString.split(IGNORE_PATTERN_DELIMITER)).toList())
                .orElse(List.of());
        SnapshotProperties snapshotProperties = SnapshotProperties.fromProperties(properties);
        return new ContextProperties(source, snapshotsHomeDir, created, excludedSubPaths, snapshotProperties);
    }

    private static IllegalPropertiesException illegalPropertiesException(Properties properties, String key) {
        return new IllegalPropertiesException("Key %s not present in %s".formatted(key, properties.keySet()));
    }

    Properties toProperties() {
        Properties properties = new Properties();
        properties.put(SOURCE_DIR_KEY, source.pathToRootDir().toString());
        properties.put(SNAPSHOTS_HOME_DIR_KEY, snapshotsHomeDir.toString());
        properties.put(CREATED_KEY, TimeUtils.asString(created));
        properties.put(IGNORE_KEY, String.join(IGNORE_PATTERN_DELIMITER, ignorePathGlobPatterns));
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
                ignore : %s
                latest snapshot
                %s""".formatted(source.pathToRootDir(), snapshotsHomeDir, TimeUtils.asString(created),
                ignorePathGlobPatterns.isEmpty() ? "None" : String.join(IGNORE_PATTERN_DELIMITER, ignorePathGlobPatterns),
                snapshotProperties == null ? "none".indent(4).stripTrailing() : snapshotProperties.toDisplayString().indent(4).stripTrailing());
    }

    public ContextProperties withSnapshotProperties(SnapshotProperties snapshotProperties) {
        return new ContextProperties(source, snapshotsHomeDir, created, ignorePathGlobPatterns, snapshotProperties);
    }

    record SnapshotProperties(Path rootDirLocation, ZonedDateTime created, int fileCount) {

        private static final String ROOT_LOCATION_KEY = "latestSnapshotRootLocation";
        private static final String CREATED_KEY = "latestSnapshotCreated";
        private static final String FILE_COUNT_KEY = "latestSnapshotFileCount";

        Properties toProperties() {
            Properties properties = new Properties();
            properties.put(ROOT_LOCATION_KEY, rootDirLocation.toString());
            properties.put(CREATED_KEY, TimeUtils.asString(created));
            properties.put(FILE_COUNT_KEY, String.valueOf(fileCount));
            return properties;
        }

        static SnapshotProperties fromProperties(Properties properties) throws IllegalPropertiesException {
            if (!properties.containsKey(ROOT_LOCATION_KEY)) {
                return null;
            }
            Path rootDirLocation = Optional.ofNullable(properties.getProperty(ROOT_LOCATION_KEY))
                    .map(Path::of)
                    .orElseThrow(() -> illegalPropertiesException(properties, ROOT_LOCATION_KEY));
            ZonedDateTime created = Optional.ofNullable(properties.getProperty(CREATED_KEY))
                    .map(TimeUtils::fromString)
                    .orElse(TimeUtils.epochStart());
            int fileCount = Optional.ofNullable(properties.getProperty(FILE_COUNT_KEY))
                    .map(Integer::parseInt)
                    .orElse(-1);
            return new SnapshotProperties(rootDirLocation, created, fileCount);
        }

        String toDisplayString() {
            return """
               location  : %s
               created   : %s
               file count: %s""".formatted(rootDirLocation, TimeUtils.asString(created), fileCount);
        }

    }

}

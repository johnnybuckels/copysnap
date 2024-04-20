package johnny.buckels.copysnap.model;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

record ContextProperties(
        Root sourceRoot,
        Path snapshotsHomeDir,
        ZonedDateTime created,
        FileSystemState latestState  // nullable
) {

    static final String CONTEXT_PROPERTIES_FILE_NAME = "context.properties";
    private static final String SOURCE_DIR_KEY = "sourceDir";
    private static final String SNAPSHOTS_HOME_DIR_KEY = "snapshotsHomeDir";
    private static final String CREATED_KEY = "created";
    private static final String REFERENCE_SNAPSHOT_KEY = "referenceSnapshot";
    private static final String LATEST_FILE_STATE_FILE_NAME = ".latest";
    private static final String PROPERTIES_EXCEPTION_TEMPLATE = "Could not find field %s in properties.";
    private static final OpenOption[] CREATE_OVERWRITE_OPEN_OPTIONS = {StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};

    static ContextProperties getNew(Path sourceDir, Path snapshotsHomeDir) {
        return new ContextProperties(Root.from(sourceDir), snapshotsHomeDir, ZonedDateTime.now(), null);
    }

    /**
     * Full FileSystemState is not loaded by this.
     */
    static ContextProperties read(Properties properties) throws IllegalPropertiesException {
        Path sourceDir = Optional.ofNullable(properties.getProperty(SOURCE_DIR_KEY)).map(Path::of)
                .orElseThrow(() -> new IllegalPropertiesException(String.format(PROPERTIES_EXCEPTION_TEMPLATE, SOURCE_DIR_KEY)));
        Path snapshotsHomeDir = Optional.ofNullable(properties.getProperty(SNAPSHOTS_HOME_DIR_KEY)).map(Path::of)
                .orElseThrow(() -> new IllegalPropertiesException(String.format(PROPERTIES_EXCEPTION_TEMPLATE, SNAPSHOTS_HOME_DIR_KEY)));
        ZonedDateTime created = Optional.ofNullable(properties.getProperty(CREATED_KEY)).map(s -> ZonedDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .orElseThrow(() -> new IllegalPropertiesException(String.format(PROPERTIES_EXCEPTION_TEMPLATE, CREATED_KEY)));
        FileSystemState.Info info;
        try (InputStream is = Files.newInputStream(snapshotsHomeDir.resolve(LATEST_FILE_STATE_FILE_NAME))) {
            info = FileSystemState.readInfo(is);
        } catch (IOException e) {
            info = null;
        }
        return new ContextProperties(Root.from(sourceDir), snapshotsHomeDir, created, new FileSystemState(info, Map.of()));
    }

    /**
     * Only tries to read {@value SNAPSHOTS_HOME_DIR_KEY} and {@value SOURCE_DIR_KEY} from the specified properties.
     * Other values are reinitialized.
     * @throws IllegalArgumentException if either {@value SNAPSHOTS_HOME_DIR_KEY} or {@value SOURCE_DIR_KEY} are not
     * specified in the specified properties.
     */
    static ContextProperties readMinimal(Properties properties) throws IllegalArgumentException {
        Path sourceDir = Optional.ofNullable(properties.getProperty(SOURCE_DIR_KEY)).map(Path::of)
                .orElseThrow(() -> new IllegalArgumentException(String.format(PROPERTIES_EXCEPTION_TEMPLATE, SOURCE_DIR_KEY)));
        Path snapshotsHomeDir = Optional.ofNullable(properties.getProperty(SNAPSHOTS_HOME_DIR_KEY)).map(Path::of)
                .orElseThrow(() -> new IllegalArgumentException(String.format(PROPERTIES_EXCEPTION_TEMPLATE, SNAPSHOTS_HOME_DIR_KEY)));
        return new ContextProperties(Root.from(sourceDir), snapshotsHomeDir, ZonedDateTime.now(), null);
    }

    ContextProperties writeAndGet() {
        Properties properties = new Properties();
        properties.put(SOURCE_DIR_KEY, sourceRoot.pathToRootDir().toString());
        properties.put(SNAPSHOTS_HOME_DIR_KEY, snapshotsHomeDir.toString());
        properties.put(CREATED_KEY, created.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        Optional.ofNullable(latestState).ifPresent(latestState -> properties.put(REFERENCE_SNAPSHOT_KEY, latestState.info().rootLocation().toString()));
        
        Path propertiesFile = snapshotsHomeDir.resolve(CONTEXT_PROPERTIES_FILE_NAME);
        try {
            Files.createDirectories(snapshotsHomeDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create snapshot home directories at %s: %s".formatted(snapshotsHomeDir, e.getMessage()), e);
        }
        try (BufferedWriter bw = Files.newBufferedWriter(propertiesFile, CREATE_OVERWRITE_OPEN_OPTIONS)) {
            properties.store(bw, "CopySnap context properties");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write properties to %s: %s".formatted(propertiesFile, e.getMessage()), e);
        }
        if (latestState != null) {
            Path latestStateFile = snapshotsHomeDir.resolve(LATEST_FILE_STATE_FILE_NAME);
            try (OutputStream os = Files.newOutputStream(latestStateFile, CREATE_OVERWRITE_OPEN_OPTIONS)) {
                latestState.write(os);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not write latest file states to %s: %s".formatted(latestStateFile, e.getMessage()), e);
            }
        }
        return this;
    }

    ContextProperties fullyLoadLatestFileState() {
        Path latestSnapshotFile = snapshotsHomeDir.resolve(LATEST_FILE_STATE_FILE_NAME);
        FileSystemState fss;
        try (InputStream is = Files.newInputStream(latestSnapshotFile)) {
            fss = FileSystemState.read(is);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read latest FileSystemState from %s: %s".formatted(latestSnapshotFile, e.getMessage()), e);
        }
        return withFileSystemState(fss);
    }

    ContextProperties withFileSystemState(FileSystemState fileSystemState) {
        return new ContextProperties(sourceRoot, snapshotsHomeDir, created, fileSystemState);
    } 

    String toDisplayString() {
        return String.format("%24s: %s", "source", sourceRoot) +
                "\n" + String.format("%24s: %s", "home", snapshotsHomeDir) +
                "\n" + String.format("%24s: %s", "created", created.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)) +
                "\n" + String.format("%24s: %s", "last modified", Optional.ofNullable(latestState.info().created()).map(t -> t.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).orElse("(not loaded)")) +
                "\n" + String.format("%24s: %s", "reference snapshot", Optional.ofNullable(latestState.info().rootLocation()).map(Path::toString).orElse("(not loaded)"));
    }



}

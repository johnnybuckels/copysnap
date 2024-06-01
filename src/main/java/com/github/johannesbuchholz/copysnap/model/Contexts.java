package com.github.johannesbuchholz.copysnap.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

public class Contexts {

    private static final String COPYSNAP_HOME_DIR_POSTFIX = "copysnap";

    private Contexts() {
        // do not instantiate
    }

    /**
     * @param sourceDir the directory to take snapshots from.
     * @param snapshotsHomeDirLocation the directory where the new context home directory should be created in.
     */
    public static Context createNew(Path sourceDir, Path snapshotsHomeDirLocation) {
        Path snapshotsHomeDir = snapshotsHomeDirLocation.resolve(sourceDir.getFileName().toString() + "-" + COPYSNAP_HOME_DIR_POSTFIX);
        ContextProperties properties;
        if (Files.isDirectory(snapshotsHomeDir)) {
            // if here, the context home directory already exists. Try to gracefully recreate context properties
            properties = findAndReadProperties(snapshotsHomeDir)
                    .map(props -> {
                        try {
                            return ContextProperties.fromProperties(props);
                        } catch (Exception e) {
                            return ContextProperties.getNew(sourceDir, snapshotsHomeDir);
                        }
                    })
                    .orElse(ContextProperties.getNew(sourceDir, snapshotsHomeDir));
        } else {
            properties = ContextProperties.getNew(sourceDir, snapshotsHomeDir);
        }
        return new Context(properties, null);
    }

    /**
     * Tries to load a context deduced from properties at the specified path. The path must point to the properties
     * file or to a directory directly containing the properties file at depth 1.
     */
    public static Context load(Path path) {
        Properties properties = findAndReadProperties(path)
                .orElseThrow(() -> new IllegalArgumentException("Could not find context properties in " + path));
        ContextProperties contextProperties = ContextProperties.fromProperties(properties);
        return new Context(contextProperties, null);
    }

    public static Context loadMinimal(Path path) {
        Properties properties = findAndReadProperties(path)
                .orElseThrow(() -> new IllegalArgumentException("Could not find context properties in " + path));
        Path sourceDirFromProperties = Optional.ofNullable(properties.getProperty(ContextProperties.SOURCE_DIR_KEY))
                .map(Path::of)
                .orElseThrow(() -> new IllegalArgumentException("Properties at %s do not contain required key %s. Try to initiate the context again.".formatted(path, ContextProperties.SOURCE_DIR_KEY)));
        Path snapshotHomeDir;
        if (Files.isDirectory(path)) {
            snapshotHomeDir = path;
        } else {
            // if here path must be a file. Otherwise, findAndReadProperties would have thrown.
            snapshotHomeDir = path.getParent();
        }
        ContextProperties contextProperties = ContextProperties.getNew(sourceDirFromProperties, snapshotHomeDir);
        return new Context(contextProperties, null);
    }

    private static Optional<Properties> findAndReadProperties(Path path) {
        Optional<Path> pathToPropertiesOpt = findPathToProperties(path);
        if (pathToPropertiesOpt.isEmpty())
            return Optional.empty();

        Path pathToProperties = pathToPropertiesOpt.get();
        Properties properties = new Properties();
        try (InputStream is = Files.newInputStream(pathToProperties, StandardOpenOption.READ)) {
            properties.load(is);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load properties from " + pathToProperties, e);
        }
        return Optional.of(properties);
    }

    private static Optional<Path> findPathToProperties(Path path) {
        Path pathToProperties;
        if (Files.isRegularFile(path)) {
            pathToProperties = path;
        } else if (Files.isDirectory(path)) {
            try (Stream<Path> pathStream = Files.find(path, 1, (p, bfa) -> bfa.isRegularFile() && p.getFileName().toString().equals(Context.CONTEXT_PROPERTIES_FILE_NAME))) {
                return pathStream.findFirst();
            } catch (IOException e) {
                throw new UncheckedIOException("Could not iterate over files in " + path, e);
            }
        } else {
            throw new IllegalArgumentException("Not a file or directory: " + path);
        }
        return Optional.of(pathToProperties);
    }

}

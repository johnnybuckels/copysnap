package com.github.johannesbuchholz.copysnap.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
        if (Files.isDirectory(snapshotsHomeDir))
            throw new IllegalStateException("Context already exists: " + snapshotsHomeDir);
        ContextProperties properties = ContextProperties.getNew(sourceDir, snapshotsHomeDir);
        return new Context(properties, null);
    }

    /**
     * Tries to load a context deduced from properties at the specified path. The path must point to the properties
     * file or to a directory directly containing the properties file at depth 1.
     */
    public static Context load(Path path) {
        Properties properties;
        try {
            properties = findAndReadProperties(path);
        } catch (ContextIOException e) {
            throw new UncheckedIOException("Could not load properties at %s: %s".formatted(path, e.getMessage()), e);
        }
        ContextProperties contextProperties = ContextProperties.fromProperties(properties);
        return new Context(contextProperties, null);
    }

    private static Properties findAndReadProperties(Path path) throws ContextIOException {
        Path pathToProperties;
        if (Files.isRegularFile(path)) {
            pathToProperties = path;
        } else if (Files.isDirectory(path)) {
            try (Stream<Path> pathStream = Files.find(path, 1, (p, bfa) -> bfa.isRegularFile() && p.getFileName().toString().equals(Context.CONTEXT_PROPERTIES_FILE_NAME))) {
               pathToProperties = pathStream.findFirst().orElseThrow(() -> new IllegalArgumentException("Could not find context properties in " + path));
            } catch (IOException e) {
                throw new ContextIOException("Could not find context properties in " + path, e);
            }
        } else {
            throw new IllegalArgumentException("Not a file or directory: " + path);
        }
        Properties properties = new Properties();
        try (InputStream is = Files.newInputStream(pathToProperties, StandardOpenOption.READ)) {
            properties.load(is);
        } catch (IOException e) {
            throw new ContextIOException("Could not load properties from " + pathToProperties, e);
        }
        return properties;
    }

}

package com.github.johannesbuchholz.copysnap;

import com.github.johannesbuchholz.copysnap.logging.ConsolePrintingLogConsumer;
import com.github.johannesbuchholz.copysnap.logging.Level;
import com.github.johannesbuchholz.copysnap.model.Context;
import com.github.johannesbuchholz.copysnap.model.Contexts;
import io.github.johannesbuchholz.clihats.core.execution.CliException;
import io.github.johannesbuchholz.clihats.core.execution.exception.CliHelpCallException;
import io.github.johannesbuchholz.clihats.processor.annotations.Argument;
import io.github.johannesbuchholz.clihats.processor.annotations.Command;
import io.github.johannesbuchholz.clihats.processor.annotations.CommandLineInterface;
import io.github.johannesbuchholz.clihats.processor.execution.CliHats;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import static io.github.johannesbuchholz.clihats.processor.annotations.Argument.Necessity.REQUIRED;
import static io.github.johannesbuchholz.clihats.processor.annotations.Argument.Type.OPERAND;

/**
 * Tool to create lightweight incremental snapshots of a file system.
 */
@CommandLineInterface(name = "copysnap")
public class Main {

    public static final String APP_VERSION = Objects.requireNonNullElse(Main.class.getPackage().getImplementationVersion(), "unknown");

    private static final String APP_NAME = ".copysnap";
    private static final String APP_PROPERTIES_FILENAME = "copysnap.properties";
    private static final Path APP_HOME_DIR =  Path.of(System.getProperty("user.home")).resolve(APP_NAME);
    private static final Path APP_PROPERTIES_PATH = APP_HOME_DIR.resolve(APP_PROPERTIES_FILENAME);
    
    private static final String CURRENT_CONTEXT_PROPERTY_NAME = "contexts.current";
    private static final Properties APP_PROPERTIES = getAppProperties();

    private static final ConsolePrintingLogConsumer CONSOLE_PRINTER = new ConsolePrintingLogConsumer(Level.INFO);

    private static Context latestContext = null;

    public static void main(String[] args) {
        try {
            CliHats.get(Main.class).executeWithThrows(args);
        } catch (CliHelpCallException e) {
            CONSOLE_PRINTER.consume(Level.INFO, e.getMessage());
        } catch (CliException e) {
            CONSOLE_PRINTER.consume(Level.ERROR, e);
        }
    }

    /**
     * Initialises a new CopySnap context sourcing the specified directory.
     * @param source The directory to take snapshots from.
     */
    @Command
    public static void init(@Argument(necessity = REQUIRED, type = OPERAND) Path source) {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path sourceDirResolved = resolvePathToCwd(source);

        Context context = Contexts.createNew(sourceDirResolved, cwd);
        Contexts.write(context);
        CONSOLE_PRINTER.consume(Level.INFO, "Initialised context at " + context.getContextHome());

        setAsCurrentContext(context);
        status();
    }

    /**
     * Loads a context.
     * @param path The path to the home directory of a context or its properties file.
     */
    @Command(name = "load")
    public static void load(@Argument(necessity = REQUIRED, type = OPERAND) Path path) {
        Path searchPathResolved = resolvePathToCwd(path);
        Context context = Contexts.load(searchPathResolved);
        CONSOLE_PRINTER.consume(Level.INFO, "Loaded context " + context.getContextHome());

        setAsCurrentContext(context);
        status();
    }

    /**
     * Displays information about the currently loaded context.
     */
    @Command
    public static void status() {
        Optional<Context> contextOpt = getLatestLoadedContext();
        if (contextOpt.isEmpty()) {
            CONSOLE_PRINTER.consume(Level.INFO, "No context loaded.");
            return;
        }
        Context context = contextOpt.get();
        CONSOLE_PRINTER.consume(Level.INFO, "Current context\n" + context.toDisplayString());
        CONSOLE_PRINTER.consume(Level.INFO, "App properties: " + APP_PROPERTIES_PATH);
        CONSOLE_PRINTER.consume(Level.INFO, "App version: " + APP_VERSION);
    }

    /**
     * Displays the version of this application.
     */
    @Command(name = "--version")
    public static void info() {
        CONSOLE_PRINTER.consume(APP_VERSION);
    }

    /**
     * Creates a new snapshot using the currently loaded context.
     */
    @Command
    public static void snapshot() {
        Optional<Context> contextOpt = getLatestLoadedContext();
        if (contextOpt.isEmpty()) {
            CONSOLE_PRINTER.consume(Level.INFO, "No context loaded.");
            return;
        }
        Context context = contextOpt.get();
        context.addConsumer(CONSOLE_PRINTER);

        context = context.loadLatestSnapshot()
                .createSnapshot();
        Contexts.write(context);

        setAsCurrentContext(context);
        status();
    }

    /**
     * Computes the file state of a specified directory and saves it as the latest file system state to the
     * current context.
     * Use this method to repair a broken or lost file system state of a previous snapshot.
     * @param directory The directory to compute a new file state of.
     */
    @Command
    public static void recompute(@Argument(necessity = REQUIRED, type = OPERAND) Path directory) {
        Path resolvedPath = resolvePathToCwd(directory);
        Optional<Context> contextOpt = getLatestLoadedContext();
        if (contextOpt.isEmpty()) {
            CONSOLE_PRINTER.consume(Level.INFO, "No context loaded.");
            return;
        }
        Context context = contextOpt.get();
        if (!resolvedPath.startsWith(context.getContextHome())) {
            CONSOLE_PRINTER.consume(Level.INFO, "Can not compute file system state outside of home path %s: %s".formatted(context.getContextHome(), resolvedPath));
        }

        context.addConsumer(CONSOLE_PRINTER);
        context = context.recomputeFileSystemState(resolvedPath);
        Contexts.write(context);

        setAsCurrentContext(context);
        status();
    }

    /**
     * Loads only essential parameters from the properties at the specified path. Other parameters are reset.
     * This method call should be followed up by 'recompute' it removes the latest file system state.
     * This is useful for resolving compatibility issues between versions of context.properties files and CopySnap.
     *
     * @param path The path to the home directory of a context or its properties file.
     */
    @Command
    public static void reset(@Argument(necessity = REQUIRED, type = OPERAND) Path path) {
        Path resolvePath = resolvePathToCwd(path);

        Context minimalContext = Contexts.loadMinimal(resolvePath);
        Contexts.write(minimalContext);
        CONSOLE_PRINTER.consume(Level.INFO, "Repaired context at " + minimalContext.getContextHome());

        setAsCurrentContext(minimalContext);
        status();
    }

    /**
     * Creates a copy of the current context's snapshot as a new snapshot by replacing symlinks with actual file copies.
     * The resulting snapshot will only consist of "hard" file copies.
     */
    @Command
    public static void solidify() {
        Optional<Context> contextOpt = getLatestLoadedContext();
        if (contextOpt.isEmpty()) {
            CONSOLE_PRINTER.consume(Level.INFO, "No context loaded.");
            return;
        }
        Context context = contextOpt.get();
        context.addConsumer(CONSOLE_PRINTER);

        context = context.solidify();
        Contexts.write(context);

        setAsCurrentContext(context);
        status();
    }

    private static Optional<Context> getLatestLoadedContext() {
        if (latestContext == null) {
            latestContext = Optional.ofNullable((String) APP_PROPERTIES.get(CURRENT_CONTEXT_PROPERTY_NAME))
                    .filter(s -> !s.isBlank())
                    .map(Path::of)
                    .map(Contexts::load)
                    .orElse(null);
        }
        return Optional.ofNullable(latestContext);
    }

    private static Properties getAppProperties() {
        Properties properties = new Properties();
        try {
            // first, load embedded fallback properties
            properties.load(Main.class.getClassLoader().getResourceAsStream(APP_PROPERTIES_FILENAME));
            if (Files.isRegularFile(APP_PROPERTIES_PATH)) {
                // then, load user properties
                try (BufferedReader br = Files.newBufferedReader(APP_PROPERTIES_PATH)) {
                    properties.load(br);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load properties: " + e.getMessage(), e);
        }
        return properties;
    }

    private static void setAsCurrentContext(Context context) {
        latestContext = context;
        APP_PROPERTIES.put(CURRENT_CONTEXT_PROPERTY_NAME, context.getContextHome().toString());
        try {
            writeAppProperties();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not update app properties: " + e.getMessage(), e);
        }
    }

    private static void writeAppProperties() throws IOException {
        Path parent = APP_PROPERTIES_PATH.getParent();
        if (parent != null)
            Files.createDirectories(parent);
        try (BufferedWriter bw = Files.newBufferedWriter(APP_PROPERTIES_PATH, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            APP_PROPERTIES.store(bw, APP_NAME + " properties");
        }
    }

    private static Path resolvePathToCwd(Path path) {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path resolved;
        if (!path.isAbsolute())
            resolved = cwd.resolve(path).normalize();
        else
            resolved = path;
        return resolved ;
    }

}
package johnny.buckels.copysnap;

import io.github.johannesbuchholz.clihats.core.execution.CliException;
import io.github.johannesbuchholz.clihats.core.execution.exception.CliHelpCallException;
import io.github.johannesbuchholz.clihats.processor.annotations.Argument;
import io.github.johannesbuchholz.clihats.processor.annotations.Command;
import io.github.johannesbuchholz.clihats.processor.annotations.CommandLineInterface;
import io.github.johannesbuchholz.clihats.processor.execution.CliHats;
import johnny.buckels.copysnap.model.Context;
import johnny.buckels.copysnap.model.ContextIOException;
import johnny.buckels.copysnap.model.Contexts;
import johnny.buckels.copysnap.service.logging.ConsolePrintingLogConsumer;
import johnny.buckels.copysnap.service.logging.Level;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Properties;

import static io.github.johannesbuchholz.clihats.processor.annotations.Argument.Necessity.REQUIRED;
import static io.github.johannesbuchholz.clihats.processor.annotations.Argument.Type.OPERAND;

/**
 * Tool to create lightweight incremental snapshots of a file system.
 */
@CommandLineInterface(name = "copysnap", description = "Tool to create differential snapshots of a filesystem.")
public class Main {

    private static final String COPYSNAP_APP_NAME = ".copysnap";
    private static final String COPYSNAP_PROPERTIES_FILENAME = "copysnap.properties";
    private static final Path COPYSNAP_HOME_DIR=  Path.of(System.getProperty("user.home")).resolve(COPYSNAP_APP_NAME);
    private static final Path COPYSNAP_APP_PROPERTIES_PATH = COPYSNAP_HOME_DIR.resolve(COPYSNAP_PROPERTIES_FILENAME);
    
    private static final String CURRENT_CONTEXT_PROPERTY_NAME = "contexts.current";
    private static final Properties APP_PROPERTIES = getAppProperties();

    private static final ConsolePrintingLogConsumer CONSOLE_PRINTER = new ConsolePrintingLogConsumer(Level.INFO);

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
    public static void init(
            @Argument(necessity = REQUIRED, type = OPERAND) Path source
    ) {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path sourceDirResolved = resolvePathToCwd(source);
        Context context = Contexts.createNew(sourceDirResolved, cwd);
        try {
            context.write();
        } catch (ContextIOException e) {
            throw new UncheckedIOException("Could not create new context: " + e.getMessage(), e);
        }

        setAsCurrentContextInAppProperties(context);
        CONSOLE_PRINTER.consume(Level.INFO, "Initialised context at " + context.getContextHome());
        status();
    }

    /**
     * Loads a context.
     * @param path The path to the home directory of a context or its properties file.
     */
    @Command(name = "load")
    public static void load(
            @Argument(necessity = REQUIRED, type = OPERAND) Path path
    ) {
        Path searchPathResolved = resolvePathToCwd(path);
        Context context= Contexts.load(searchPathResolved);

        setAsCurrentContextInAppProperties(context);
        CONSOLE_PRINTER.consume(Level.INFO, "Loaded context " + context.getContextHome());
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
        CONSOLE_PRINTER.consume(Level.INFO, "CopySnap properties: " + COPYSNAP_APP_PROPERTIES_PATH);
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
        try {
            context.loadLatestSnapshot()
                    .createSnapshot()
                    .write();
        } catch (ContextIOException e) {
            throw new UncheckedIOException("Could not create snapshot: " + e.getMessage(), e);
        }
        CONSOLE_PRINTER.consume(Level.INFO, "Created new snapshot in " + context.getContextHome());
        status();
    }

    /**
     * Computes the file state of a specified directory and saves it as "latest" file system state to the current context.
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
        context.addConsumer(CONSOLE_PRINTER);
        if (!resolvedPath.startsWith(context.getContextHome())) {
            CONSOLE_PRINTER.consume(Level.INFO, "Can not compute file system state outside of home path %s: %s".formatted(context.getContextHome(), resolvedPath));
        }
        try {
            context.recomputeFileSystemState(resolvedPath)
                    .write();
        } catch (ContextIOException e) {
            throw new UncheckedIOException("Could not recompute file system state: " + e.getMessage(), e);
        }
        CONSOLE_PRINTER.consume(Level.INFO, "Recomputed file states at " + resolvedPath);
        status();
    }

    private static Optional<Context> getLatestLoadedContext() {
        return Optional.ofNullable((String) APP_PROPERTIES.get(CURRENT_CONTEXT_PROPERTY_NAME))
                .filter(s -> !s.isBlank())
                .map(Path::of)
                .map(Contexts::load);
    }

    private static Properties getAppProperties() {
        Properties properties = new Properties();
        try {
            // first, load embedded fallback properties
            properties.load(Main.class.getClassLoader().getResourceAsStream(COPYSNAP_PROPERTIES_FILENAME));
            if (Files.isRegularFile(COPYSNAP_APP_PROPERTIES_PATH)) {
                // then, load user properties
                try (BufferedReader br = Files.newBufferedReader(COPYSNAP_APP_PROPERTIES_PATH)) {
                    properties.load(br);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load properties: " + e.getMessage(), e);
        }
        return properties;
    }

    private static void setAsCurrentContextInAppProperties(Context context) {
        APP_PROPERTIES.put(CURRENT_CONTEXT_PROPERTY_NAME, context.getContextHome().toString());
        try {
            writeAppProperties();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not update app properties: " + e.getMessage(), e);
        }
    }

    private static void writeAppProperties() throws IOException {
        Path parent = COPYSNAP_APP_PROPERTIES_PATH.getParent();
        if (parent != null)
            Files.createDirectories(parent);
        try (BufferedWriter bw = Files.newBufferedWriter(COPYSNAP_APP_PROPERTIES_PATH, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            APP_PROPERTIES.store(bw, COPYSNAP_APP_NAME + " properties");
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
package johnny.buckels.copysnap;

import de.jb.clihats.processor.annotations.Command;
import de.jb.clihats.processor.annotations.CommandLineInterface;
import de.jb.clihats.processor.annotations.Option;
import de.jb.clihats.processor.annotations.OptionNecessity;
import de.jb.clihats.processor.execution.CliHats;
import johnny.buckels.copysnap.model.Context;
import johnny.buckels.copysnap.model.Contexts;
import johnny.buckels.copysnap.service.logging.DefaultMessageConsumer;
import johnny.buckels.copysnap.service.logging.Message;
import johnny.buckels.copysnap.service.logging.MessageConsumer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Properties;

/**
 * Tool to create lightweight incremental snapshots of a file system.
 */
@CommandLineInterface(name = "copysnap", description = "Tool to create differential snapshots of a filesystem.")
public class Main {

    private static final String COPYSNAP_APP_NAME = ".copysnap";
    private static final String COPYSNAP_PROPERTIES_FILENAME = "copysnap.properties";
    private static final Path COPYSNAP_HOME_DIR=  Path.of(System.getProperty("user.home")).resolve(COPYSNAP_APP_NAME);
    private static final Path COPYSNAP_DEFAULT_PROPERTIES=  COPYSNAP_HOME_DIR.resolve(COPYSNAP_PROPERTIES_FILENAME);
    
    private static final String CURRENT_CONTEXT_PROPERTY_NAME = "contexts.current";
    private static final String PARALLELISM_PROPERTY_NAME = "cpu.parallelism";
    private static final Properties APP_PROPERTIES = getAppProperties();

    private static final MessageConsumer MESSAGE_CONSUMER = getMessageConsumer();

    public static void main(String[] args) {
        CliHats.get(Main.class).execute(args);
    }

    /**
     * Initialises a new copy snap context sourcing the specified directory.
     */
    @Command(name = "init")
    public static void initContext(
            @Option(name = {"-s", "--source"}, necessity = OptionNecessity.REQUIRED, description = "The directory to take snapshots from.") Path sourceDir
    ) {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path sourceDirResolved = resolvePathToCwd(sourceDir);
        Context context = Contexts.createNew(sourceDirResolved, cwd);
        saveContextAndAppProperties(context);
        MESSAGE_CONSUMER.consumeMessage(Message.info("Initialised context"));
        MESSAGE_CONSUMER.consumeMessage(Message.info(context.toDisplayString()));
    }

    /**
     * Loads a context.
     */
    @Command(name = "load")
    public static void load(@Option(name = {"-p", "--path"}, necessity = OptionNecessity.REQUIRED, description = "The path to the home directory of a context or its properties files.") Path searchPath) {
        Path searchPathResolved = resolvePathToCwd(searchPath);
        Context context = Contexts.findPropertiesAndLoadContext(searchPathResolved);
        saveContextAndAppProperties(context);
        MESSAGE_CONSUMER.consumeMessage(Message.info("Loaded context"));
        MESSAGE_CONSUMER.consumeMessage(Message.info(context.toDisplayString()));
    }

    /**
     * Displays information about the currently loaded context.
     * FIXME: "last snapshot" is not displayed correctly.
     */
    @Command(name = "current")
    public static void getCurrentContextInfo() {
        getLatestLoadedContext()
                .ifPresentOrElse(
                        context -> {
                            MESSAGE_CONSUMER.consumeMessage(Message.info("Current context"));
                            MESSAGE_CONSUMER.consumeMessage(Message.info(context.toDisplayString()));
                        },
                        () -> MESSAGE_CONSUMER.consumeMessage(Message.info("No context loaded."))
                );
    }

    /**
     * Creates a new snapshot using the currently loaded context.
     * @param quiet If set, no console output will be printed.
     */
    @Command(name = "create-snapshot")
    public static void createSnapshot(
            @Option(name = {"-q", "--quiet"}, flagValue = "true", defaultValue = "false") Boolean quiet
    ) {
        getLatestLoadedContext()
                .map(c -> c
                        .withMessageConsumer(quiet ? MessageConsumer.quiet() : MESSAGE_CONSUMER)
                        .withCpuParallelism(Double.parseDouble((String) APP_PROPERTIES.getOrDefault(PARALLELISM_PROPERTY_NAME, "1.0"))))
                .ifPresentOrElse(
                        Context::createSnapshot,
                        () -> MESSAGE_CONSUMER.consumeMessage(Message.info("No context loaded."))
                );
    }

    /**
     * Computes the file state of a specified directory and saves it to the current context.
     * This method is intended to repair broken or lost file states of a previous snapshot.
     * Example: "copysnap recompute -d /path/to/my/copysnap-home/sourcename-copysnap/2022-11-20-16-21-27/sourcename"
     * @param path The directory to compute a new file state of.
     * @param quiet If set, no console output will be printed.
     */
    @Command(name = "recompute")
    public static void recomputeFileState(
            @Option(name = {"-d", "--directory"}, necessity = OptionNecessity.REQUIRED) Path path,
            @Option(name = {"-q", "--quiet"}, flagValue = "true", defaultValue = "false") Boolean quiet
    ) {
        Path resolvedPath = resolvePathToCwd(path);
        getLatestLoadedContext()
                .map(c -> c
                        .withMessageConsumer(quiet ? MessageConsumer.quiet() : MESSAGE_CONSUMER)
                        .withCpuParallelism(Double.parseDouble((String) APP_PROPERTIES.getOrDefault(PARALLELISM_PROPERTY_NAME, "1.0"))))
                .ifPresentOrElse(
                        c -> c.recomputeFileSystemStateAndSave(resolvedPath),
                        () -> MESSAGE_CONSUMER.consumeMessage(Message.info("No context loaded."))
                );
    }

    private static Optional<Context> getLatestLoadedContext() {
        return Optional.ofNullable((String) APP_PROPERTIES.get(CURRENT_CONTEXT_PROPERTY_NAME))
                .filter(s -> !s.isBlank())
                .map(Path::of)
                .map(Contexts::findPropertiesAndLoadContext);
    }

    private static MessageConsumer getMessageConsumer() {
        Console console = System.console();
        if (console != null)
            return new DefaultMessageConsumer(console.writer());
        else
            return new DefaultMessageConsumer();
    }

    private static Properties getAppProperties() {
        Properties properties = new Properties();
        try {
            // first, load embedded fallback properties
            properties.load(Main.class.getClassLoader().getResourceAsStream(COPYSNAP_PROPERTIES_FILENAME));
            if (Files.isRegularFile(COPYSNAP_DEFAULT_PROPERTIES)) {
                // then, load user properties
                try (BufferedReader br = Files.newBufferedReader(COPYSNAP_DEFAULT_PROPERTIES)) {
                    properties.load(br);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load properties", e);
        }
        return properties;
    }

    /**
     * Saves context properties to its home directory as well as in the user's copysnap properties file.
     */
    private static void saveContextAndAppProperties(Context context) {
        Path path = Contexts.saveContextProperties(context);
        APP_PROPERTIES.put(CURRENT_CONTEXT_PROPERTY_NAME, path.toString());
        try {
            writeAppProperties();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not update app properties", e);
        }
    }

    private static void writeAppProperties() throws IOException {
        Path parent = COPYSNAP_DEFAULT_PROPERTIES.getParent();
        if (parent != null)
            Files.createDirectories(parent);
        try (BufferedWriter bw = Files.newBufferedWriter(COPYSNAP_DEFAULT_PROPERTIES, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
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
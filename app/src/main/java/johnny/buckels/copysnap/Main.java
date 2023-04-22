package johnny.buckels.copysnap;

import io.github.johannesbuchholz.clihats.processor.annotations.Command;
import io.github.johannesbuchholz.clihats.processor.annotations.CommandLineInterface;
import io.github.johannesbuchholz.clihats.processor.annotations.Option;
import io.github.johannesbuchholz.clihats.processor.annotations.OptionNecessity;
import io.github.johannesbuchholz.clihats.processor.execution.CliHats;
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

    private static final MessageConsumer MESSAGE_CONSUMER = newMessageConsumer();

    public static void main(String[] args) {
        CliHats.get(Main.class).execute(args);
    }

    /**
     * Initialises a new copy snap context sourcing the specified directory.
     * @param source The directory to take snapshots from.
     */
    @Command
    public static void init(
            @Option(necessity = OptionNecessity.REQUIRED, position = 0) Path source
    ) {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path sourceDirResolved = resolvePathToCwd(source);
        Context context = Contexts.createNew(sourceDirResolved, cwd);

        Path contextPropertiesPath = context.writeProperties();
        saveContextAndAppProperties(contextPropertiesPath);

        MESSAGE_CONSUMER.consumeMessage(Message.info("Initialised context"));
        MESSAGE_CONSUMER.consumeMessage(Message.info(context.toDisplayString()));
    }

    /**
     * Loads a context.
     * @param path The path to the home directory of a context or its properties file.
     */
    @Command(name = "load")
    public static void load(
            @Option(necessity = OptionNecessity.REQUIRED, position = 0) Path path
    ) {
        Path searchPathResolved = resolvePathToCwd(path);
        Context context = Contexts.load(searchPathResolved);

        Path contextPropertiesPath = context.writeProperties();
        saveContextAndAppProperties(contextPropertiesPath);

        MESSAGE_CONSUMER.consumeMessage(Message.info("Loaded context"));
        MESSAGE_CONSUMER.consumeMessage(Message.info(context.toDisplayString()));
    }

    /**
     * Displays information about the currently loaded context.
     */
    @Command
    public static void status() {
        Optional<Context> contextOpt = getLatestLoadedContext();
        if (contextOpt.isEmpty()) {
            MESSAGE_CONSUMER.consumeMessage(Message.info("No context loaded."));
            return;
        }
        Context context = contextOpt.get();

        MESSAGE_CONSUMER.consumeMessage(Message.info("Current context"));
        MESSAGE_CONSUMER.consumeMessage(Message.info(context.toDisplayString()));
    }

    /**
     * Creates a new snapshot using the currently loaded context.
     * @param quiet If set, no console output will be printed.
     */
    @Command
    public static void snapshot(
            @Option(flagValue = "true", defaultValue = "false") Boolean quiet
    ) {
        Optional<Context> contextOpt = getLatestLoadedContext();
        if (contextOpt.isEmpty()) {
            MESSAGE_CONSUMER.consumeMessage(Message.info("No context loaded."));
            return;
        }
        Context context = contextOpt.get().withMessageConsumer(quiet ? MessageConsumer.quiet() : MESSAGE_CONSUMER);
        context.createSnapshot(computeThreadCount());

        Path contextPropertiesPath = context.writeProperties();
        saveContextAndAppProperties(contextPropertiesPath);
    }

    /**
     * Loads only essential parameters from the specified path. Other parameters are reset. This method call should be
     * followed up by 'recompute' as the latest file system state dropped.
     * This is useful for resolving compatibility issues between versions of context.properties files and CopySnap.
     * @param path The path to the home directory of a context or its properties file.
     */
    @Command
    public static void repair(
            @Option(necessity = OptionNecessity.REQUIRED, position = 0) Path path
    ) {
        Path resolvePath = resolvePathToCwd(path);
        Context context = Contexts.repairAndLoad(resolvePath);

        Path contextPropertiesPath = context.writeProperties();
        saveContextAndAppProperties(contextPropertiesPath);

        MESSAGE_CONSUMER.consumeMessage(Message.info("Repaired context."));
        MESSAGE_CONSUMER.consumeMessage(Message.info(context.toDisplayString()));
    }

    /**
     * Computes the file state of a specified directory and saves it to the current context.
     * This method intends to repair a broken or lost file state of a previous snapshot.
     * @param directory The directory to compute a new file state of.
     * @param quiet If set, no console output will be printed.
     */
    @Command
    public static void recompute(
            @Option(necessity = OptionNecessity.REQUIRED, position = 0) Path directory,
            @Option(flagValue = "true", defaultValue = "false") Boolean quiet
    ) {
        Path resolvedPath = resolvePathToCwd(directory);
        Optional<Context> contextOpt = getLatestLoadedContext();
        if (contextOpt.isEmpty()) {
            MESSAGE_CONSUMER.consumeMessage(Message.info("No context loaded."));
            return;
        }
        Context context = contextOpt.get().withMessageConsumer(quiet ? MessageConsumer.quiet() : MESSAGE_CONSUMER);
        context.recomputeFileSystemState(resolvedPath, computeThreadCount());

        Path contextPropertiesPath = context.writeProperties();
        saveContextAndAppProperties(contextPropertiesPath);
    }

    /**
     * @return integer from [0, Runtime.getRuntime().availableProcessors()] according to specified parallelism.
     */
    private static int computeThreadCount() {
        Double parallelism = Optional.ofNullable((String) APP_PROPERTIES.get(PARALLELISM_PROPERTY_NAME)).map(Double::parseDouble).orElse(1.);
        return (int) Math.round(Math.max(1, Runtime.getRuntime().availableProcessors() * Math.min(1, parallelism)));
    }

    private static Optional<Context> getLatestLoadedContext() {
        return Optional.ofNullable((String) APP_PROPERTIES.get(CURRENT_CONTEXT_PROPERTY_NAME))
                .filter(s -> !s.isBlank())
                .map(Path::of)
                .map(Contexts::load);
    }

    private static MessageConsumer newMessageConsumer() {
        Console console = System.console();
        if (console != null) {
            return new DefaultMessageConsumer(console.writer());
        } else {
            return new DefaultMessageConsumer();
        }
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
            throw new UncheckedIOException("Could not load properties: " + e.getMessage(), e);
        }
        return properties;
    }

    /**
     * Saves context properties to its home directory as well as in the user's copysnap properties file.
     */
    private static void saveContextAndAppProperties(Path latestContextPath) {
        APP_PROPERTIES.put(CURRENT_CONTEXT_PROPERTY_NAME, latestContextPath.toString());
        try {
            writeAppProperties();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not update app properties: " + e.getMessage(), e);
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
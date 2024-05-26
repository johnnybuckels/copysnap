package johnny.buckels.copysnap.service.logging;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Stream;

public class AbstractLogProducer implements LogProducer {

    protected final Set<LogConsumer> logConsumers;


    public AbstractLogProducer() {
        this(new HashSet<>());
    }

    public AbstractLogProducer(Set<LogConsumer> logConsumers) {
        this.logConsumers = logConsumers;
    }

    @Override
    public void addConsumer(LogConsumer logConsumer) {
        logConsumers.add(Objects.requireNonNull(logConsumer));
    }

    @Override
    public void removeConsumer(LogConsumer logConsumer) {
        logConsumers.remove(logConsumer);
    }

    protected void log(Level level, String message) {
        getRelevantConsumers(level).forEach(mc -> mc.consume(level, message));
    }

    protected void logStacktrace(Level level, Throwable e) {
        getRelevantConsumers(level).forEach(logConsumer -> logConsumer.consume(level, getStackStrace(e)));
    }

    private Stream<LogConsumer> getRelevantConsumers(Level level) {
        return logConsumers.stream().filter(logConsumer -> logConsumer.isLevelRelevant(level));
    }

    private String getStackStrace(Throwable e) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(out);
        e.printStackTrace(printStream);
        return out.toString();
    }

}

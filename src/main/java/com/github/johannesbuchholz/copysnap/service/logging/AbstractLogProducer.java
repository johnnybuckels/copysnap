package com.github.johannesbuchholz.copysnap.service.logging;

import com.github.johannesbuchholz.copysnap.util.TimeUtils;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

    protected void logTaskStart(Level level, String taskMessage, ZonedDateTime start, Object... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("key value pairs must be of even length: " + keyValuePairs.length);
        }
        StringBuilder sb = new StringBuilder(taskMessage);
        sb.append(" - started: ").append(TimeUtils.asString(start));
        if (keyValuePairs.length > 0) {
            String keyValuePairString = IntStream.range(0, keyValuePairs.length / 2)
                    .mapToObj(i -> "%s: %s".formatted(keyValuePairs[2 * i], keyValuePairs[2 * i + 1]))
                    .collect(Collectors.joining(", "));
            sb.append(", ").append(keyValuePairString);
        }
        log(level, sb.toString());
    }

    protected void logTaskEnd(Level level, String taskMessage, Duration d) {
        log(level, "%s (%s ms)".formatted(taskMessage, d.toMillis()));
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

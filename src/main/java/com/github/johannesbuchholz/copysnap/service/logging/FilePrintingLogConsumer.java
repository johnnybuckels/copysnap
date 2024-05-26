package com.github.johannesbuchholz.copysnap.service.logging;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FilePrintingLogConsumer implements LogConsumer, AutoCloseable  {

    private final PrintWriter writer;
    private final Level level;

    public static FilePrintingLogConsumer at(Path path) {
        OutputStream os;
        try {
            os = Files.newOutputStream(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create output stream at " + path, e);
        }
        PrintWriter pw = new PrintWriter(os, true, StandardCharsets.UTF_8);
        return new FilePrintingLogConsumer(Level.DEBUG, pw);
    }

    private FilePrintingLogConsumer(Level level, PrintWriter writer) {
        this.writer = writer;
        this.level = level;
    }

    @Override
    public void consume(Level level, String line) {
        if (!isLevelRelevant(level)) {
            return;
        }
        writer.println("[%s] %s".formatted(level, line));
    }

    @Override
    public Level level() {
        return level;
    }

    @Override
    public void close() {
        writer.close();
    }

}

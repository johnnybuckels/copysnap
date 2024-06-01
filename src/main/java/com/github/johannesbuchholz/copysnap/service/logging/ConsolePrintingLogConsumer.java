package com.github.johannesbuchholz.copysnap.service.logging;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public record ConsolePrintingLogConsumer(Level level) implements LogConsumer {

    private static final PrintStream OUT = System.out;

    private static final String FORMAT = "[%s] %s%n";

    public void consume(String message) {
        consume(Level.NONE, message);
    }

    public void consume(Level level, Throwable e) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(out);
        e.printStackTrace(printStream);
        consume(level, out.toString());
    }

    @Override
    public void consume(Level level, String message) {
        if (!isLevelRelevant(level)) {
            return;
        }
        if (level == Level.NONE) {
            OUT.println(message);
        } else {
            OUT.printf(FORMAT, level.name(), message);
            OUT.flush();
        }
    }

}

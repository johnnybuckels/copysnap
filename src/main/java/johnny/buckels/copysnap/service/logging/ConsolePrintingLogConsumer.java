package johnny.buckels.copysnap.service.logging;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public record ConsolePrintingLogConsumer(Level level) implements LogConsumer {

    private static final PrintStream OUT = System.out;

    private static final String FORMAT = "[%s] %s%n";

    @Override
    public void consume(Level level, String line) {
        if (!isLevelRelevant(level)) {
            return;
        }
        OUT.printf(FORMAT, level.name(), line);
        OUT.flush();
    }

    public void consume(Level level, Throwable e) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(out);
        e.printStackTrace(printStream);
        consume(level, out.toString());
    }

}

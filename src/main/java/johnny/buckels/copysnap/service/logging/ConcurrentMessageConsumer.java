package johnny.buckels.copysnap.service.logging;

import java.io.PrintWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConcurrentMessageConsumer implements MessageConsumer {

    private static final String CURSOR_TO_START = "\r";

    ExecutorService executor = Executors.newSingleThreadExecutor();

    private final PrintWriter printWriter;

    public ConcurrentMessageConsumer() {
        this(new PrintWriter(System.out));
    }

    public ConcurrentMessageConsumer(PrintWriter printWriter) {
        this.printWriter = printWriter;
    }

    @Override
    public void consumeMessage(Message message) {
        executor.execute(() -> {
            printWriter.println(message);
            printWriter.flush();
        });
    }

    @Override
    public void consumeMessage(Message message, Throwable e) {
        executor.execute(() -> {
            printWriter.println(message);
            e.printStackTrace(printWriter);
        });
    }

    @Override
    public void consumeMessageOverride(Message message) {
        executor.execute(() -> printWriter.print(CURSOR_TO_START + message));
    }

    @Override
    public void newLine() {
        executor.execute(printWriter::println);
    }

    @Override
    public void close() {
        executor.shutdownNow();
        printWriter.close();
    }

}

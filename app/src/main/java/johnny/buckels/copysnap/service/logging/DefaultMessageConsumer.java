package johnny.buckels.copysnap.service.logging;

import java.io.PrintWriter;

public class DefaultMessageConsumer implements MessageConsumer {

    private static final String CURSOR_TO_START = "\r";

    private final PrintWriter printWriter;

    public DefaultMessageConsumer() {
        this(new PrintWriter(System.out));
    }

    public DefaultMessageConsumer(PrintWriter printWriter) {
        this.printWriter = printWriter;
    }

    @Override
    public void consumeMessage(Message message) {
        printWriter.println(message);
    }

    @Override
    public void consumeMessageOverride(Message message) {
        printWriter.print(CURSOR_TO_START + message);
    }

    @Override
    public void newLine() {
        printWriter.println();
    }

}

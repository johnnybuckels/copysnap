package johnny.buckels.copysnap.service.logging;


import java.io.Closeable;

/**
 * Implementing classes are able to consume messages.
 * MessageConsumer instances of this are intended to be a member of a {@link MessageProducer}.
 */
public interface MessageConsumer extends Closeable {

    void consumeMessage(Message message);

    /**
     * The specified message replaces the previously consumed message.
     */
    void consumeMessageOverride(Message message);

    void newLine();

    /**
     * Stops this consumer from accepting new messages.
     */
    @Override
    void close();

    static MessageConsumer quiet() {
        return new MessageConsumer() {
            @Override
            public void consumeMessage(Message message) {
            }

            @Override
            public void consumeMessageOverride(Message message) {
            }

            @Override
            public void newLine() {
            }

            @Override
            public void close() {
            }
        };
    }

}

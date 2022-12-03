package johnny.buckels.copysnap.service.logging;


/**
 * Implementing classes are able to consume messages.
 * MessageConsumer instances of this are intended to be a member of a {@link MessageProducer}.
 */
public interface MessageConsumer {

    void consumeMessage(Message message);

    /**
     * The specified message replaces the previously consumed message.
     */
    void consumeMessageOverride(Message message);

    void newLine();

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
        };
    }

}

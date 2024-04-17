package johnny.buckels.copysnap.service.logging;

/**
 * Implementing classes are able to produce messages to one or more accepted {@link MessageConsumer} instances.
 */
public interface MessageProducer {

    /**
     * Fluent-Api style setter returning itself after accepting the specified {@link MessageConsumer}.
     */
    void setMessageConsumer(MessageConsumer messageConsumer);

}

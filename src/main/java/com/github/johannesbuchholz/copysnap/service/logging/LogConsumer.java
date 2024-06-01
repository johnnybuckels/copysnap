package com.github.johannesbuchholz.copysnap.service.logging;

/**
 * Implementing classes are able to consume messages.
 * MessageConsumer instances of this are intended to be a member of a {@link LogProducer}.
 */
public interface LogConsumer {

    void consume(Level level, String message);

    Level level();

    default boolean isLevelRelevant(Level level) {
        return level().compareTo(level) <= 0;
    }

}

package com.github.johannesbuchholz.copysnap.service.logging;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AbstractLogProducerTest {

    static class TestProducer extends AbstractLogProducer {

        TestProducer() {
            super();
        }
    }

    static class TestConsumer implements LogConsumer {

        private record Log(Level level, String message) {}

        private final List<Log> logs = new ArrayList<>();

        private final Level level;

        TestConsumer(Level level) {
            this.level = level;
        }

        @Override
        public void consume(Level level, String line) {
            if (!isLevelRelevant(level))
                return;
            logs.add(new Log(level, line));
        }

        @Override
        public Level level() {
            return level;
        }

        List<Log> getLogs() {
            return logs;
        }

    }

    @Test
    void testProducingLogs() {
        TestProducer testProducer = new TestProducer();
        TestConsumer testConsumerDebug = new TestConsumer(Level.DEBUG);
        TestConsumer testConsumerInfo = new TestConsumer(Level.INFO);
        TestConsumer testConsumerError = new TestConsumer(Level.ERROR);
        testProducer.addConsumer(testConsumerDebug);
        testProducer.addConsumer(testConsumerInfo);
        testProducer.addConsumer(testConsumerError);

        String messageDebug = "debug-message";
        String messageInfo = "info-message";
        String messageError = "error-message";

        testProducer.log(Level.DEBUG, messageDebug);
        testProducer.log(Level.INFO, messageInfo);
        testProducer.log(Level.ERROR, messageError);

        assertEquals(List.of(
                new TestConsumer.Log(Level.DEBUG, messageDebug),
                new TestConsumer.Log(Level.INFO, messageInfo),
                new TestConsumer.Log(Level.ERROR, messageError)),
                testConsumerDebug.getLogs());
        assertEquals(List.of(
                        new TestConsumer.Log(Level.INFO, messageInfo),
                        new TestConsumer.Log(Level.ERROR, messageError)),
                testConsumerInfo.getLogs());
        assertEquals(List.of(
                        new TestConsumer.Log(Level.ERROR, messageError)),
                testConsumerError.getLogs());
    }

}
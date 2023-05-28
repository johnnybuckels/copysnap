package johnny.buckels.copysnap.service.logging;

import java.util.Objects;

public class AbstractMessageProducer implements MessageProducer {

    protected MessageConsumer messageConsumer = MessageConsumer.quiet();

    @Override
    public void setMessageConsumer(MessageConsumer messageConsumer) {
        this.messageConsumer = Objects.requireNonNull(messageConsumer);
    }

}

package johnny.buckels.copysnap.service.logging;

import java.util.Objects;

public class AbstractMessageProducer implements MessageProducer {

    protected MessageConsumer messageConsumer = new DefaultMessageConsumer();

    @Override
    public void setMessageConsumer(MessageConsumer messageConsumer) {
        this.messageConsumer = Objects.requireNonNull(messageConsumer);
    }

}

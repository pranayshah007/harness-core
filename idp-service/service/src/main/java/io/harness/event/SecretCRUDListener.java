package io.harness.event;

import io.harness.eventsframework.consumer.Message;
import io.harness.ng.core.event.MessageListener;

public class SecretCRUDListener implements MessageListener {

    // TODO: Need to add a binding for this.
    @Override
    public boolean handleMessage(Message message) {
        return false;
    }
}

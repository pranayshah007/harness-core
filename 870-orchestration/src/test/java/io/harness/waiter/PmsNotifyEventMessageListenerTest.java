package io.harness.waiter;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.pms.sdk.execution.events.NotifyEventHandler;

import com.google.common.util.concurrent.MoreExecutors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

public class PmsNotifyEventMessageListenerTest {
  private NotifyEventHandler eventHandler;

  @Before
  public void setup() {
    eventHandler = new NotifyEventHandler();
  }

  @Test
  @Category(UnitTests.class)
  public void shouldValidateEventMessageListener() {
    PmsNotifyEventMessageListener messageListener = Mockito.spy(
        new PmsNotifyEventMessageListener("RANDOM_SERVICE", eventHandler, MoreExecutors.newDirectExecutorService()));

    Boolean listenerProcessable = messageListener.isProcessable(Message.newBuilder().build());
    assertThat(listenerProcessable).isEqualTo(true);
  }
}

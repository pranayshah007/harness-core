package io.harness.waiter;

import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Producer;
import io.harness.rule.Owner;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PmsNotifyEventPublisherTest {
  @Mock Producer producerClient;
  @InjectMocks PmsNotifyEventPublisher pmsNotifyEventPublisher;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateEventPublisher() {
    pmsNotifyEventPublisher.send(NotifyEvent.Builder.aNotifyEvent().waitInstanceId("as").build());
    verify(producerClient, times(1)).send(any());
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.execution;

import static io.harness.pms.events.PmsEventFrameworkConstants.SERVICE_NAME;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.pipeline.executions.CDSdkOrchestrationEventHandler;
import io.harness.cdng.pipeline.executions.CdngOrchestrationEventMessageListener;
import io.harness.eventsframework.consumer.Message;
import io.harness.rule.Owner;

import com.google.common.util.concurrent.MoreExecutors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.CDP)
public class CdngOrchestrationEventMessageListenerTest {
  private CDSdkOrchestrationEventHandler eventHandler;

  @Before
  public void setup() {
    eventHandler = new CDSdkOrchestrationEventHandler();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldReturnTrueForCDEvent() {
    CdngOrchestrationEventMessageListener messageListener = Mockito.spy(
        new CdngOrchestrationEventMessageListener("cd", eventHandler, MoreExecutors.newDirectExecutorService()));

    Boolean listenerProcessable = messageListener.isProcessable(
        Message.newBuilder()
            .setMessage(
                io.harness.eventsframework.producer.Message.newBuilder().putMetadata(SERVICE_NAME, "cd").build())
            .build());
    assertThat(listenerProcessable).isTrue();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldReturnTrueForPMSEvent() {
    CdngOrchestrationEventMessageListener messageListener = Mockito.spy(
        new CdngOrchestrationEventMessageListener("cd", eventHandler, MoreExecutors.newDirectExecutorService()));

    Boolean listenerProcessable = messageListener.isProcessable(
        Message.newBuilder()
            .setMessage(
                io.harness.eventsframework.producer.Message.newBuilder().putMetadata(SERVICE_NAME, "pms").build())
            .build());
    assertThat(listenerProcessable).isTrue();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldReturnFalseIfServiceIsNotCdOrPms() {
    CdngOrchestrationEventMessageListener messageListener = Mockito.spy(
        new CdngOrchestrationEventMessageListener("cd", eventHandler, MoreExecutors.newDirectExecutorService()));

    Boolean listenerProcessable = messageListener.isProcessable(
        Message.newBuilder()
            .setMessage(
                io.harness.eventsframework.producer.Message.newBuilder().putMetadata(SERVICE_NAME, "any").build())
            .build());
    assertThat(listenerProcessable).isFalse();
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.event.triggerwebhookevent;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PIPELINE_ENTITY;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.pms.contracts.interrupts.InterruptEvent;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.triggers.webhook.service.TriggerWebhookEventExecutionService;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class TriggerExecutionEventStreamListenerTest extends CategoryTest {
  @Mock private TriggerWebhookEventExecutionService triggerWebhookEventExecutionService;
  @InjectMocks TriggerExecutionEventStreamListener triggerExecutionEventStreamListener;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testHandleMessage() {
    assertTrue(triggerExecutionEventStreamListener.handleMessage(Message.newBuilder().build()));
    Message message =
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putMetadata(ENTITY_TYPE, PIPELINE_ENTITY)
                            .putMetadata(ACTION, DELETE_ACTION)
                            .setData(InterruptEvent.newBuilder().setType(InterruptType.ABORT).build().toByteString())
                            .build())
            .build();
    triggerExecutionEventStreamListener.handleMessage(message);
    verify(triggerWebhookEventExecutionService, times(1)).processEvent(any());
  }
}

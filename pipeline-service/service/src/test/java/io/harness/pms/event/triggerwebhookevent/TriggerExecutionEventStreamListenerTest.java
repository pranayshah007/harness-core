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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.webhookpayloads.webhookdata.EventHeader;
import io.harness.eventsframework.webhookpayloads.webhookdata.TriggerExecutionDTO;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.pms.triggers.webhook.service.TriggerWebhookEventExecutionService;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
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
    TriggerExecutionDTO triggerExecutionDTO =
        TriggerExecutionDTO.newBuilder()
            .setAccountId("accId")
            .setOrgIdentifier("orgId")
            .setProjectIdentifier("projId")
            .setTriggerIdentifier("triggerId")
            .setTargetIdentifier("pipId")
            .setWebhookDto(WebhookDTO.newBuilder()
                               .addHeaders(EventHeader.newBuilder().setKey("key1").addValues("value1").build())
                               .build())
            .build();

    Map<String, String> metadata = new HashMap<>();
    metadata.put(ENTITY_TYPE, PIPELINE_ENTITY);
    metadata.put(ACTION, DELETE_ACTION);
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(metadata)
                                          .setData(triggerExecutionDTO.toByteString())
                                          .build())
                          .build();
    triggerExecutionEventStreamListener.handleMessage(message, System.currentTimeMillis());
    verify(triggerWebhookEventExecutionService, times(1))
        .handleEvent(eq(triggerExecutionDTO), eq(metadata), anyLong(), anyLong());
  }
}

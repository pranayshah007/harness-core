/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.pollingevent;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PIPELINE_ENTITY;
import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.pms.contracts.interrupts.InterruptEvent;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.triggers.build.eventmapper.BuildTriggerEventMapper;
import io.harness.pms.triggers.webhook.helpers.TriggerEventExecutionHelper;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;

@OwnedBy(PIPELINE)
@PrepareForTest(TriggerEventResponseHelper.class)
public class PollingEventStreamListenerTest extends CategoryTest {
  @Mock private BuildTriggerEventMapper mapper;
  @Mock private TriggerEventExecutionHelper triggerEventExecutionHelper;
  @Mock private TriggerEventHistoryRepository triggerEventHistoryRepository;
  @InjectMocks PollingEventStreamListener pollingEventStreamListener;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testHandleMessage() {
    assertTrue(pollingEventStreamListener.handleMessage(Message.newBuilder().build()));
    doReturn(WebhookEventMappingResponse.builder().failedToFindTrigger(false).build())
        .when(mapper)
        .consumeBuildTriggerEvent(any());
    Message message =
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putMetadata(ENTITY_TYPE, PIPELINE_ENTITY)
                            .putMetadata(ACTION, DELETE_ACTION)
                            .setData(InterruptEvent.newBuilder().setType(InterruptType.ABORT).build().toByteString())
                            .build())
            .build();
    pollingEventStreamListener.handleMessage(message);
    verify(triggerEventHistoryRepository, times(0)).save(any());
    doReturn(Collections.singletonList(TriggerEventResponse.builder().build()))
        .when(triggerEventExecutionHelper)
        .processTriggersForActivation(any(), any());
    assertThatThrownBy(() -> pollingEventStreamListener.handleMessage(message));
    Mockito.mockStatic(TriggerEventResponseHelper.class);
    pollingEventStreamListener.handleMessage(message);
    verify(triggerEventHistoryRepository, times(1)).save(any());
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.entitycrud;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PIPELINE_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.USER_ENTITY;
import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.contracts.interrupts.InterruptEvent;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.rule.Owner;

import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PipelineEntityCRUDStreamListenerTest extends CategoryTest {
  @Mock private NGTriggerService ngTriggerService;
  @InjectMocks PipelineEntityCRUDStreamListener pipelineEntityCRUDStreamListener;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testHandleMessage() {
    Message message = Message.newBuilder().build();
    assertTrue(pipelineEntityCRUDStreamListener.handleMessage(message));
    message =
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putMetadata(ENTITY_TYPE, PIPELINE_ENTITY)
                            .putMetadata(ACTION, DELETE_ACTION)
                            .setData(InterruptEvent.newBuilder().setType(InterruptType.ABORT).build().toByteString())
                            .build())
            .build();
    pipelineEntityCRUDStreamListener.handleMessage(message);
    verify(ngTriggerService, times(1)).deleteAllForPipeline(any(), any(), any(), any());
    message = Message.newBuilder()
                  .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                  .putMetadata(ENTITY_TYPE, PIPELINE_ENTITY)
                                  .putMetadata(ACTION, CREATE_ACTION)
                                  .setData(ByteString.copyFromUtf8("Dummy"))
                                  .build())
                  .build();
    Message finalMessage = message;
    assertThatCode(() -> pipelineEntityCRUDStreamListener.handleMessage(finalMessage))
        .isInstanceOf(InvalidRequestException.class);

    message =
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putMetadata(ENTITY_TYPE, PIPELINE_ENTITY)
                            .putMetadata(ACTION, CREATE_ACTION)
                            .setData(InterruptEvent.newBuilder().setType(InterruptType.ABORT).build().toByteString())
                            .build())
            .build();
    pipelineEntityCRUDStreamListener.handleMessage(message);

    message =
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putMetadata(ENTITY_TYPE, USER_ENTITY)
                            .putMetadata(ACTION, CREATE_ACTION)
                            .setData(InterruptEvent.newBuilder().setType(InterruptType.ABORT).build().toByteString())
                            .build())
            .build();
    pipelineEntityCRUDStreamListener.handleMessage(message);
    verify(ngTriggerService, times(1)).deleteAllForPipeline(any(), any(), any(), any());
  }
}

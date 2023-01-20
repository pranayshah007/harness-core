/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.entitycrud;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PIPELINE_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.USER_ENTITY;
import static io.harness.rule.OwnerRule.ARCHIT;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.interrupts.InterruptEvent;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.rule.Owner;
import io.harness.steps.resourcerestraint.service.ResourceRestraintService;

import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class AccountEntityCrudStreamListenerTest extends CategoryTest {
  @Mock private ResourceRestraintService resourceRestraintService;
  @InjectMocks AccountEntityCrudStreamListener accountEntityCrudStreamListener;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testEmptyHandleMessage() {
    Message message = Message.newBuilder().build();
    assertTrue(accountEntityCrudStreamListener.handleMessage(message));
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testNonPipelineEntityEventHandleMessage() {
    // Action type is not delete and even entity type is not account
    Message message =
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putMetadata(ENTITY_TYPE, USER_ENTITY)
                            .putMetadata(ACTION, CREATE_ACTION)
                            .setData(InterruptEvent.newBuilder().setType(InterruptType.ABORT).build().toByteString())
                            .build())
            .build();
    assertTrue(accountEntityCrudStreamListener.handleMessage(message));
    // Zero interaction with any one of pipeline metadata delete
    verify(resourceRestraintService, times(0)).deleteAllRestraintForGivenAccount(any());

    // Action type is not delete but entity is account
    message =
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putMetadata(ENTITY_TYPE, ACCOUNT_ENTITY)
                            .putMetadata(ACTION, CREATE_ACTION)
                            .setData(InterruptEvent.newBuilder().setType(InterruptType.ABORT).build().toByteString())
                            .build())
            .build();
    assertTrue(accountEntityCrudStreamListener.handleMessage(message));
    // Zero interaction with any one of pipeline metadata delete
    verify(resourceRestraintService, times(0)).deleteAllRestraintForGivenAccount(any());

    // Data is not parsable into EntityChangeDTO
    message = Message.newBuilder()
                  .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                  .putMetadata(ENTITY_TYPE, ACCOUNT_ENTITY)
                                  .putMetadata(ACTION, CREATE_ACTION)
                                  .setData(ByteString.copyFromUtf8("Dummy"))
                                  .build())
                  .build();
    Message finalMessage = message;
    assertThatCode(() -> accountEntityCrudStreamListener.handleMessage(finalMessage))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testHandleMessage() {
    String ACCOUNT_ID = "accountId";

    Message message =
        Message.newBuilder()
            .setMessage(
                io.harness.eventsframework.producer.Message.newBuilder()
                    .putMetadata(ENTITY_TYPE, ACCOUNT_ENTITY)
                    .putMetadata(ACTION, DELETE_ACTION)
                    .setData(AccountEntityChangeDTO.newBuilder().setAccountId(ACCOUNT_ID).build().toByteString())
                    .build())
            .build();

    assertTrue(accountEntityCrudStreamListener.handleMessage(message));

    // Verify pipeline metadata delete
    verify(resourceRestraintService, times(1)).deleteAllRestraintForGivenAccount(any());
  }
}

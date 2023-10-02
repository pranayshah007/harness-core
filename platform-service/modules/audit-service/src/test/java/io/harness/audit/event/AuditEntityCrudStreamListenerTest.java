/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.event;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.rule.OwnerRule.SAHIBA;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.audit.api.impl.AuditServiceImpl;
import io.harness.audit.eventframework.AuditEntityCrudStreamListener;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AuditEntityCrudStreamListenerTest extends CategoryTest {
  @Mock private AuditServiceImpl auditServiceImpl;
  @InjectMocks AuditEntityCrudStreamListener auditEntityCrudStreamListener;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testEmptyHandleMessage() {
    Message message = Message.newBuilder().build();
    assertTrue(auditEntityCrudStreamListener.handleMessage(message));
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testAuditDeleteEventHandleMessage() {
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, ACCOUNT_ENTITY)
                                          .putMetadata(ACTION, DELETE_ACTION)
                                          .build())
                          .build();
    assertTrue(auditEntityCrudStreamListener.handleMessage(message));
    verify(auditServiceImpl, times(1)).deleteAuditInfo(any());
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testEventHandleMessageWithNonDeleteAction() {
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, ACCOUNT_ENTITY)
                                          .putMetadata(ACTION, UPDATE_ACTION)
                                          .build())
                          .build();
    assertTrue(auditEntityCrudStreamListener.handleMessage(message));
    verify(auditServiceImpl, times(0)).deleteAuditInfo(any());
  }
}

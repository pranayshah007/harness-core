/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.services.impl;

import static io.harness.rule.OwnerRule.NISHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.audit.entities.AuditEvent;
import io.harness.auditevent.streaming.entities.outgoing.OutgoingAuditMessage;
import io.harness.auditevent.streaming.mappers.AuditEventMapper;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class BatchProcessorServiceImplTest extends CategoryTest {
  @Mock private AuditEventMapper auditEventMapper;
  private BatchProcessorServiceImpl batchProcessorService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    this.batchProcessorService = new BatchProcessorServiceImpl(auditEventMapper);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testProcessAuditEvent() {
    List<AuditEvent> auditEvents = List.of(AuditEvent.builder().build());
    when(auditEventMapper.toOutgoingAuditMessage(any())).thenReturn(OutgoingAuditMessage.builder().build());

    List<OutgoingAuditMessage> outgoingMessageList = batchProcessorService.processAuditEvent(auditEvents);

    assertThat(outgoingMessageList).isNotEmpty();
    assertThat(outgoingMessageList).hasSize(auditEvents.size());
    verify(auditEventMapper, times(auditEvents.size())).toOutgoingAuditMessage(any());
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.outbox;

import static io.harness.delegate.beans.DelegateType.KUBERNETES;
import static io.harness.rule.OwnerRule.JENNY;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

import io.harness.CategoryTest;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.client.api.AuditClientService;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.delegate.beans.DelegateSetupDetails;
import io.harness.delegate.events.DelegateRegisterEvent;
import io.harness.delegate.events.DelegateUnregisterEvent;
import io.harness.delegate.events.DelegateUpsertEvent;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

public class DelegateOutboxEventHandlerTest extends CategoryTest {
  private DelegateOutboxEventHandler delegateOutboxEventHandler;
  private ObjectMapper objectMapper = new ObjectMapper();
  private AuditClientService auditClientService;
  private GlobalContext globalContext;
  private String accountIdentifier;

  @Before
  public void setup() {
    auditClientService = mock(AuditClientService.class);
    delegateOutboxEventHandler = new DelegateOutboxEventHandler(auditClientService);
    globalContext = new GlobalContext();
    accountIdentifier = randomAlphabetic(10);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateRegisterAuditEvent() throws Exception {
    DelegateSetupDetails delegateSetupDetails =
        DelegateSetupDetails.builder().tags(Set.of("tag2")).identifier("_iden2").delegateType(KUBERNETES).build();
    DelegateRegisterEvent delegateRegisterEvent =
        DelegateRegisterEvent.builder()
            .accountIdentifier(accountIdentifier)
            .orgIdentifier(delegateSetupDetails.getOrgIdentifier())
            .projectIdentifier(delegateSetupDetails.getProjectIdentifier())
            .delegateSetupDetails(DelegateSetupDetails.builder()
                                      .identifier(delegateSetupDetails.getIdentifier())
                                      .delegateType(delegateSetupDetails.getDelegateType())
                                      .tokenName(delegateSetupDetails.getTokenName())
                                      .tags(delegateSetupDetails.getTags())
                                      .build())
            .build();

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType(delegateRegisterEvent.getEventType())
                                  .globalContext(globalContext)
                                  .resourceScope(delegateRegisterEvent.getResourceScope())
                                  .eventData(objectMapper.writeValueAsString(delegateRegisterEvent))
                                  .resource(delegateRegisterEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    delegateOutboxEventHandler.handleDelegateRegisterEvent(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertEquals(Action.CREATE, auditEntry.getAction());
    assertEquals(auditEntry.getNewYaml(), NGYamlUtils.getYamlString(delegateSetupDetails));
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateUnRegisterAuditEvent() throws Exception {
    DelegateSetupDetails delegateSetupDetails =
        DelegateSetupDetails.builder().tags(Set.of("tag1")).identifier("_iden2").delegateType(KUBERNETES).build();
    DelegateUnregisterEvent delegateUnregisterEvent =
        DelegateUnregisterEvent.builder()
            .accountIdentifier(accountIdentifier)
            .orgIdentifier(delegateSetupDetails.getOrgIdentifier())
            .projectIdentifier(delegateSetupDetails.getProjectIdentifier())
            .delegateSetupDetails(DelegateSetupDetails.builder()
                                      .identifier(delegateSetupDetails.getIdentifier())
                                      .delegateType(delegateSetupDetails.getDelegateType())
                                      .tokenName(delegateSetupDetails.getTokenName())
                                      .tags(delegateSetupDetails.getTags())
                                      .build())
            .build();

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType(delegateUnregisterEvent.getEventType())
                                  .globalContext(globalContext)
                                  .resourceScope(delegateUnregisterEvent.getResourceScope())
                                  .eventData(objectMapper.writeValueAsString(delegateUnregisterEvent))
                                  .resource(delegateUnregisterEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    delegateOutboxEventHandler.handleDelegateUnRegisterEvent(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertEquals(Action.DELETE, auditEntry.getAction());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateUpsertAuditEvent() throws Exception {
    DelegateSetupDetails delegateSetupDetails =
        DelegateSetupDetails.builder().tags(Set.of("tag1")).identifier("_iden2").delegateType(KUBERNETES).build();

    DelegateUpsertEvent delegateUpsertEvent =
        DelegateUpsertEvent.builder()
            .accountIdentifier(accountIdentifier)
            .delegateSetupDetails(DelegateSetupDetails.builder()
                                      .identifier(delegateSetupDetails.getIdentifier())
                                      .delegateType(delegateSetupDetails.getDelegateType())
                                      .tokenName(delegateSetupDetails.getTokenName())
                                      .tags(delegateSetupDetails.getTags())
                                      .build())
            .build();
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType(delegateUpsertEvent.getEventType())
                                  .globalContext(globalContext)
                                  .resourceScope(delegateUpsertEvent.getResourceScope())
                                  .eventData(objectMapper.writeValueAsString(delegateUpsertEvent))
                                  .resource(delegateUpsertEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    delegateOutboxEventHandler.handleDelegateUpsertEvent(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertEquals(Action.UPSERT, auditEntry.getAction());
    assertEquals(auditEntry.getNewYaml(), NGYamlUtils.getYamlString(delegateSetupDetails));
  }
}

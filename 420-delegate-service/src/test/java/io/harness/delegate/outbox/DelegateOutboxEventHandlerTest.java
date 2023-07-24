package io.harness.delegate.outbox;

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
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    DelegateRegisterEvent delegateRegisterEvent = DelegateRegisterEvent.builder()
                                                      .accountIdentifier(accountIdentifier)
                                                      .delegateSetupDetails(DelegateSetupDetails.builder().build())
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
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateUnRegisterAuditEvent() throws Exception {
    DelegateUnregisterEvent delegateUnregisterEvent = DelegateUnregisterEvent.builder()
                                                          .accountIdentifier(accountIdentifier)
                                                          .delegateSetupDetails(DelegateSetupDetails.builder().build())
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
    delegateOutboxEventHandler.handleDelegateRegisterEvent(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertEquals(Action.CREATE, auditEntry.getAction());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateUpsertAuditEvent() throws Exception {
    DelegateUpsertEvent delegateUpsertEvent = DelegateUpsertEvent.builder()
                                                  .accountIdentifier(accountIdentifier)
                                                  .delegateSetupDetails(DelegateSetupDetails.builder().build())
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
    delegateOutboxEventHandler.handleDelegateRegisterEvent(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertEquals(Action.CREATE, auditEntry.getAction());
  }
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;
import static io.harness.rule.OwnerRule.NISHANT;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.ResourceTypeConstants;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.client.api.AuditClientService;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.events.VariableCreateEvent;
import io.harness.ng.core.variable.dto.VariableDTO;
import io.harness.ng.core.variable.dto.VariableRequestDTO;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextData;
import io.harness.security.dto.UserPrincipal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class VariableEventHandlerTest extends CategoryTest {
  private ObjectMapper objectMapper;
  @Mock private AuditClientService auditClientService;
  private VariableEventHandler variableEventHandler;

  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  @Before
  public void setup() {
    objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    MockitoAnnotations.initMocks(this);
    variableEventHandler = new VariableEventHandler(auditClientService);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testHandleVariableCreateEvent() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    VariableDTO variableDTO = getVariableDTO(orgIdentifier, projectIdentifier, identifier);
    VariableCreateEvent variableCreateEvent = new VariableCreateEvent(accountIdentifier, variableDTO);
    String eventData = objectMapper.writeValueAsString(variableCreateEvent);
    GlobalContext globalContext = new GlobalContext();
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder()
            .principal(new UserPrincipal(
                randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10)))
            .build();
    globalContext.setGlobalContextRecord(sourcePrincipalContextData);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("VariableCreated")
                                  .globalContext(globalContext)
                                  .eventData(eventData)
                                  .resourceScope(variableCreateEvent.getResourceScope())
                                  .resource(variableCreateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    String newYaml = getYamlString(VariableRequestDTO.builder().variable(variableDTO).build());

    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verifyMethodInvocation(outboxEvent, auditEntryArgumentCaptor);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.CREATE, auditEntry.getAction());
    assertNull(auditEntry.getOldYaml());
    assertEquals(newYaml, auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testHandle_notSupportedEventType() {
    String eventType = randomAlphabetic(10);
    OutboxEvent event = OutboxEvent.builder().eventType(eventType).build();
    exceptionRule.expect(InvalidArgumentsException.class);
    exceptionRule.expectMessage(String.format("Not supported event type %s", eventType));
    variableEventHandler.handle(event);
  }

  private VariableDTO getVariableDTO(String orgIdentifier, String projectIdentifier, String identifier) {
    return VariableDTO.builder()
        .identifier(identifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private void verifyMethodInvocation(OutboxEvent outboxEvent, ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor) {
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    variableEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
  }

  private void assertAuditEntry(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String identifier, AuditEntry auditEntry, OutboxEvent outboxEvent) {
    assertNotNull(auditEntry);
    assertEquals(outboxEvent.getId(), auditEntry.getInsertId());
    assertEquals(ResourceTypeConstants.VARIABLE, auditEntry.getResource().getType());
    assertEquals(identifier, auditEntry.getResource().getIdentifier());
    assertEquals(accountIdentifier, auditEntry.getResourceScope().getAccountIdentifier());
    assertEquals(orgIdentifier, auditEntry.getResourceScope().getOrgIdentifier());
    assertEquals(projectIdentifier, auditEntry.getResourceScope().getProjectIdentifier());
    assertEquals(ModuleType.CORE, auditEntry.getModule());
    assertEquals(outboxEvent.getCreatedAt().longValue(), auditEntry.getTimestamp());
    assertNull(auditEntry.getEnvironment());
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.events.SecretForceDeleteEvent.SECRET_FORCE_DELETED;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;
import static io.harness.rule.OwnerRule.KARAN;
import static io.harness.rule.OwnerRule.MEENAKSHI;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.ResourceTypeConstants;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.client.api.AuditClientService;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretRequestWrapper;
import io.harness.ng.core.events.SecretCreateEvent;
import io.harness.ng.core.events.SecretDeleteEvent;
import io.harness.ng.core.events.SecretForceDeleteEvent;
import io.harness.ng.core.events.SecretUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(PL)
public class SecretEventHandlerTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private AuditClientService auditClientService;
  private SecretEventHandler secretEventHandler;

  @Before
  public void setup() {
    objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    auditClientService = mock(AuditClientService.class);
    secretEventHandler = spy(new SecretEventHandler(auditClientService));
  }

  private SecretDTOV2 getSecretDTOV2(String orgIdentifier, String identifier) {
    return SecretDTOV2.builder().orgIdentifier(orgIdentifier).identifier(identifier).name(randomAlphabetic(10)).build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    SecretDTOV2 secretDTO = getSecretDTOV2(orgIdentifier, identifier);
    SecretCreateEvent secretCreateEvent = new SecretCreateEvent(accountIdentifier, secretDTO);
    String eventData = objectMapper.writeValueAsString(secretCreateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("SecretCreated")
                                  .eventData(eventData)
                                  .resourceScope(secretCreateEvent.getResourceScope())
                                  .resource(secretCreateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    String newYaml = getYamlString(SecretRequestWrapper.builder().secret(secretDTO).build());

    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verifyMethodInvocation(outboxEvent, auditEntryArgumentCaptor);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.CREATE, auditEntry.getAction());
    assertNull(auditEntry.getOldYaml());
    assertEquals(newYaml, auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    SecretDTOV2 oldSecretDTOV2 = getSecretDTOV2(orgIdentifier, identifier);
    SecretDTOV2 newSecretDTOV2 = getSecretDTOV2(orgIdentifier, identifier);
    SecretUpdateEvent secretUpdateEvent = new SecretUpdateEvent(accountIdentifier, newSecretDTOV2, oldSecretDTOV2);
    String eventData = objectMapper.writeValueAsString(secretUpdateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("SecretUpdated")
                                  .eventData(eventData)
                                  .resourceScope(secretUpdateEvent.getResourceScope())
                                  .resource(secretUpdateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    String oldYaml = getYamlString(SecretRequestWrapper.builder().secret(oldSecretDTOV2).build());
    String newYaml = getYamlString(SecretRequestWrapper.builder().secret(newSecretDTOV2).build());

    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verifyMethodInvocation(outboxEvent, auditEntryArgumentCaptor);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
    assertEquals(oldYaml, auditEntry.getOldYaml());
    assertEquals(newYaml, auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    SecretDTOV2 secretDTO = getSecretDTOV2(orgIdentifier, identifier);
    SecretDeleteEvent secretDeleteEvent = new SecretDeleteEvent(accountIdentifier, secretDTO);
    String eventData = objectMapper.writeValueAsString(secretDeleteEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("SecretDeleted")
                                  .eventData(eventData)
                                  .resourceScope(secretDeleteEvent.getResourceScope())
                                  .resource(secretDeleteEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    String oldYaml = getYamlString(SecretRequestWrapper.builder().secret(secretDTO).build());

    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verifyMethodInvocation(outboxEvent, auditEntryArgumentCaptor);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.DELETE, auditEntry.getAction());
    assertNull(auditEntry.getNewYaml());
    assertEquals(oldYaml, auditEntry.getOldYaml());
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testForceDelete() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    SecretDTOV2 secretDTO = getSecretDTOV2(orgIdentifier, identifier);
    SecretForceDeleteEvent secretDeleteEvent = new SecretForceDeleteEvent(accountIdentifier, secretDTO);
    String eventData = objectMapper.writeValueAsString(secretDeleteEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType(SECRET_FORCE_DELETED)
                                  .eventData(eventData)
                                  .resourceScope(secretDeleteEvent.getResourceScope())
                                  .resource(secretDeleteEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    String oldYaml = getYamlString(SecretRequestWrapper.builder().secret(secretDTO).build());

    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verifyMethodInvocation(outboxEvent, auditEntryArgumentCaptor);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.FORCE_DELETE, auditEntry.getAction());
    assertNull(auditEntry.getNewYaml());
    assertEquals(oldYaml, auditEntry.getOldYaml());
  }

  private void verifyMethodInvocation(OutboxEvent outboxEvent, ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor) {
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    secretEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
  }

  private void assertAuditEntry(String accountIdentifier, String orgIdentifier, String identifier,
      AuditEntry auditEntry, OutboxEvent outboxEvent) {
    assertNotNull(auditEntry);
    assertEquals(outboxEvent.getId(), auditEntry.getInsertId());
    assertEquals(ResourceTypeConstants.SECRET, auditEntry.getResource().getType());
    assertEquals(identifier, auditEntry.getResource().getIdentifier());
    assertEquals(accountIdentifier, auditEntry.getResourceScope().getAccountIdentifier());
    assertEquals(orgIdentifier, auditEntry.getResourceScope().getOrgIdentifier());
    assertNull(auditEntry.getResourceScope().getProjectIdentifier());
    assertEquals(ModuleType.CORE, auditEntry.getModule());
    assertEquals(outboxEvent.getCreatedAt().longValue(), auditEntry.getTimestamp());
    assertNull(auditEntry.getEnvironment());
  }
}

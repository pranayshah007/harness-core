/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze;

import static io.harness.events.FreezeEntityCreateEvent.DEPLOYMENT_FREEZE_CREATED;
import static io.harness.events.FreezeEntityDeleteEvent.DEPLOYMENT_FREEZE_DELETED;
import static io.harness.events.FreezeEntityUpdateEvent.DEPLOYMENT_FREEZE_UPDATED;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.YUVRAJ;

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
import io.harness.FreezeOutboxEventHandler;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.client.api.AuditClientService;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.events.FreezeEntityCreateEvent;
import io.harness.events.FreezeEntityDeleteEvent;
import io.harness.events.FreezeEntityUpdateEvent;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.beans.yaml.FreezeConfig;
import io.harness.freeze.beans.yaml.FreezeInfoConfig;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextData;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(HarnessTeam.CDC)
public class FreezeOutboxEventHandlerTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private AuditClientService auditClientService;
  private FreezeOutboxEventHandler freezeOutboxEventHandler;

  @Before
  public void setup() {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    auditClientService = mock(AuditClientService.class);
    freezeOutboxEventHandler = spy(new FreezeOutboxEventHandler(auditClientService));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCreate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String newYaml = getYamlString(FreezeConfig.builder()
                                       .freezeInfoConfig(FreezeInfoConfig.builder()
                                                             .identifier(identifier)
                                                             .orgIdentifier(orgIdentifier)
                                                             .projectIdentifier(projectIdentifier)
                                                             .build())
                                       .build());
    FreezeConfigEntity freezeConfig = FreezeConfigEntity.builder()
                                          .type(FreezeType.MANUAL)
                                          .accountId(accountIdentifier)
                                          .identifier(identifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .yaml(newYaml)
                                          .build();
    FreezeEntityCreateEvent freezeEntityCreateEvent =
        FreezeEntityCreateEvent.builder().createdFreeze(freezeConfig).accountIdentifier(accountIdentifier).build();
    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);
    String eventData = objectMapper.writeValueAsString(freezeEntityCreateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(DEPLOYMENT_FREEZE_CREATED)
                                  .resourceScope(freezeEntityCreateEvent.getResourceScope())
                                  .resource(freezeEntityCreateEvent.getResource())
                                  .globalContext(globalContext)
                                  .eventData(eventData)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    freezeOutboxEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.CREATE, auditEntry.getAction());
    assertNull(auditEntry.getOldYaml());
    assertEquals(newYaml, auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testCreateGlobalFreeze() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String newYaml = getYamlString(FreezeConfig.builder()
                                       .freezeInfoConfig(FreezeInfoConfig.builder()
                                                             .identifier(identifier)
                                                             .orgIdentifier(orgIdentifier)
                                                             .projectIdentifier(projectIdentifier)
                                                             .build())
                                       .build());
    FreezeConfigEntity freezeConfig = FreezeConfigEntity.builder()
                                          .type(FreezeType.GLOBAL)
                                          .accountId(accountIdentifier)
                                          .identifier(identifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .yaml(newYaml)
                                          .build();
    FreezeEntityCreateEvent freezeEntityCreateEvent =
        FreezeEntityCreateEvent.builder().createdFreeze(freezeConfig).accountIdentifier(accountIdentifier).build();
    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);
    String eventData = objectMapper.writeValueAsString(freezeEntityCreateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(DEPLOYMENT_FREEZE_CREATED)
                                  .resourceScope(freezeEntityCreateEvent.getResourceScope())
                                  .resource(freezeEntityCreateEvent.getResource())
                                  .globalContext(globalContext)
                                  .eventData(eventData)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .build();
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    freezeOutboxEventHandler.handle(outboxEvent);
    verify(auditClientService, times(0)).publishAudit(any(), any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testDelete() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String oldYaml = getYamlString(FreezeConfig.builder()
                                       .freezeInfoConfig(FreezeInfoConfig.builder()
                                                             .identifier(identifier)
                                                             .orgIdentifier(orgIdentifier)
                                                             .projectIdentifier(projectIdentifier)
                                                             .build())
                                       .build());
    FreezeConfigEntity freezeConfig = FreezeConfigEntity.builder()
                                          .type(FreezeType.MANUAL)
                                          .accountId(accountIdentifier)
                                          .identifier(identifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .yaml(oldYaml)
                                          .build();
    FreezeEntityDeleteEvent freezeEntityDeleteEvent =
        FreezeEntityDeleteEvent.builder().accountIdentifier(accountIdentifier).deletedFreeze(freezeConfig).build();
    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);
    String eventData = objectMapper.writeValueAsString(freezeEntityDeleteEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(DEPLOYMENT_FREEZE_DELETED)
                                  .resourceScope(freezeEntityDeleteEvent.getResourceScope())
                                  .resource(freezeEntityDeleteEvent.getResource())
                                  .globalContext(globalContext)
                                  .eventData(eventData)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    freezeOutboxEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.DELETE, auditEntry.getAction());
    assertNull(auditEntry.getNewYaml());
    assertEquals(oldYaml, auditEntry.getOldYaml());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String oldYaml = getYamlString(FreezeConfig.builder()
                                       .freezeInfoConfig(FreezeInfoConfig.builder()
                                                             .identifier(identifier)
                                                             .orgIdentifier(orgIdentifier)
                                                             .projectIdentifier(projectIdentifier)
                                                             .name("freeze")
                                                             .build())
                                       .build());
    FreezeConfigEntity oldFreezeConfig = FreezeConfigEntity.builder()
                                             .type(FreezeType.MANUAL)
                                             .accountId(accountIdentifier)
                                             .identifier(identifier)
                                             .orgIdentifier(orgIdentifier)
                                             .projectIdentifier(projectIdentifier)
                                             .name("freeze")
                                             .yaml(oldYaml)
                                             .build();
    String newYaml = getYamlString(FreezeConfig.builder()
                                       .freezeInfoConfig(FreezeInfoConfig.builder()
                                                             .identifier(identifier)
                                                             .orgIdentifier(orgIdentifier)
                                                             .projectIdentifier(projectIdentifier)
                                                             .name("freezeUpdated")
                                                             .build())
                                       .build());
    FreezeConfigEntity newFreezeConfig = FreezeConfigEntity.builder()
                                             .type(FreezeType.MANUAL)
                                             .accountId(accountIdentifier)
                                             .identifier(identifier)
                                             .orgIdentifier(orgIdentifier)
                                             .projectIdentifier(projectIdentifier)
                                             .name("freezeUpdated")
                                             .yaml(newYaml)
                                             .build();
    FreezeEntityUpdateEvent freezeEntityUpdateEvent = FreezeEntityUpdateEvent.builder()
                                                          .newFreeze(newFreezeConfig)
                                                          .accountIdentifier(accountIdentifier)
                                                          .oldFreeze(oldFreezeConfig)
                                                          .build();

    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);
    String eventData = objectMapper.writeValueAsString(freezeEntityUpdateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(DEPLOYMENT_FREEZE_UPDATED)
                                  .resourceScope(freezeEntityUpdateEvent.getResourceScope())
                                  .resource(freezeEntityUpdateEvent.getResource())
                                  .globalContext(globalContext)
                                  .eventData(eventData)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    freezeOutboxEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
    assertEquals(oldYaml, auditEntry.getOldYaml());
    assertEquals(newYaml, auditEntry.getNewYaml());
  }

  private void assertAuditEntry(String accountId, String orgIdentifier, String projectIdentifier, String identifier,
      AuditEntry auditEntry, OutboxEvent outboxEvent) {
    assertNotNull(auditEntry);
    assertEquals(accountId, auditEntry.getResourceScope().getAccountIdentifier());
    assertEquals(orgIdentifier, auditEntry.getResourceScope().getOrgIdentifier());
    assertEquals(projectIdentifier, auditEntry.getResourceScope().getProjectIdentifier());
    assertEquals(auditEntry.getInsertId(), outboxEvent.getId());
    assertEquals(identifier, auditEntry.getResource().getIdentifier());
    assertEquals(ModuleType.CORE, auditEntry.getModule());
    assertEquals(outboxEvent.getCreatedAt().longValue(), auditEntry.getTimestamp());
  }
}

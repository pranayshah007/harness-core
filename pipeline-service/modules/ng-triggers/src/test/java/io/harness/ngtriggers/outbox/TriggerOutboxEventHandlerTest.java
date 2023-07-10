/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.outbox;

import static io.harness.rule.OwnerRule.JAMIE;

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
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.client.api.AuditClientService;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.events.TriggerCreateEvent;
import io.harness.ngtriggers.events.TriggerDeleteEvent;
import io.harness.ngtriggers.events.TriggerOutboxEvents;
import io.harness.ngtriggers.events.TriggerUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextData;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(HarnessTeam.CI)
public class TriggerOutboxEventHandlerTest extends CategoryTest {
  private AuditClientService auditClientService;
  private TriggerOutboxEventHandler eventHandler;
  String newYaml;
  String oldYaml;

  @Before
  public void setup() throws IOException {
    auditClientService = mock(AuditClientService.class);
    eventHandler = spy(new TriggerOutboxEventHandler(auditClientService));
    newYaml = "pipeline:\n"
        + "  identifier: secrethttp1\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: qaStage\n"
        + "        spec:\n"
        + "          infrastructure:\n"
        + "            infrastructureDefinition:\n"
        + "              spec:\n"
        + "                releaseName: releaseName1";
    oldYaml = "pipeline:\n"
        + "  identifier: secrethttp2\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: qaStage\n"
        + "        spec:\n"
        + "          infrastructure:\n"
        + "            infrastructureDefinition:\n"
        + "              spec:\n"
        + "                releaseName: releaseName2";
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testCreate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    NGTriggerEntity triggerEntity = NGTriggerEntity.builder()
                                        .name(randomAlphabetic(10))
                                        .identifier(identifier)
                                        .type(NGTriggerType.WEBHOOK)
                                        .yaml(newYaml)
                                        .build();
    TriggerCreateEvent triggerCreateEvent =
        new TriggerCreateEvent(accountIdentifier, orgIdentifier, projectIdentifier, triggerEntity);
    String eventData = YamlUtils.writeYamlString(triggerCreateEvent);
    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .resource(triggerCreateEvent.getResource())
                                  .resourceScope(triggerCreateEvent.getResourceScope())
                                  .eventType(TriggerOutboxEvents.TRIGGER_CREATED)
                                  .blocked(false)
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .eventData(eventData)
                                  .id(randomAlphabetic(10))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    eventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.CREATE, auditEntry.getAction());
    assertNull(auditEntry.getOldYaml());
    assertEquals(newYaml, auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testUpdate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    NGTriggerEntity newTrigger = NGTriggerEntity.builder()
                                     .name(randomAlphabetic(10))
                                     .identifier(identifier)
                                     .type(NGTriggerType.WEBHOOK)
                                     .yaml(newYaml)
                                     .build();
    NGTriggerEntity oldTrigger = NGTriggerEntity.builder()
                                     .name(randomAlphabetic(10))
                                     .identifier(identifier)
                                     .type(NGTriggerType.SCHEDULED)
                                     .yaml(oldYaml)
                                     .build();
    TriggerUpdateEvent triggerUpdateEvent =
        new TriggerUpdateEvent(accountIdentifier, orgIdentifier, projectIdentifier, oldTrigger, newTrigger);
    String eventData = YamlUtils.writeYamlString(triggerUpdateEvent);
    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .resource(triggerUpdateEvent.getResource())
                                  .resourceScope(triggerUpdateEvent.getResourceScope())
                                  .eventType(TriggerOutboxEvents.TRIGGER_UPDATED)
                                  .blocked(false)
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .eventData(eventData)
                                  .id(randomAlphabetic(10))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    eventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
    assertEquals(newYaml, auditEntry.getNewYaml());
    assertEquals(oldYaml, auditEntry.getOldYaml());
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testDelete() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    NGTriggerEntity triggerEntity = NGTriggerEntity.builder()
                                        .name(randomAlphabetic(10))
                                        .identifier(identifier)
                                        .type(NGTriggerType.WEBHOOK)
                                        .yaml(oldYaml)
                                        .build();
    TriggerDeleteEvent triggerDeleteEvent =
        new TriggerDeleteEvent(accountIdentifier, orgIdentifier, projectIdentifier, triggerEntity);
    String eventData = YamlUtils.writeYamlString(triggerDeleteEvent);
    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .resource(triggerDeleteEvent.getResource())
                                  .resourceScope(triggerDeleteEvent.getResourceScope())
                                  .eventType(TriggerOutboxEvents.TRIGGER_DELETED)
                                  .blocked(false)
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .eventData(eventData)
                                  .id(randomAlphabetic(10))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    eventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.DELETE, auditEntry.getAction());
    assertNull(auditEntry.getNewYaml());
    assertEquals(oldYaml, auditEntry.getOldYaml());
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

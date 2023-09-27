/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.outbox;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.ROHITKARELIA;

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
import io.harness.audit.beans.custom.executions.NodeExecutionEventData;
import io.harness.audit.beans.custom.executions.TriggeredByInfoAuditDetails;
import io.harness.audit.client.api.AuditClientService;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.engine.pms.audits.events.NodeExecutionOutboxEventConstants;
import io.harness.engine.pms.audits.events.PipelineAbortEvent;
import io.harness.engine.pms.audits.events.PipelineEndEvent;
import io.harness.engine.pms.audits.events.PipelineStartEvent;
import io.harness.engine.pms.audits.events.StageEndEvent;
import io.harness.engine.pms.audits.events.StageStartEvent;
import io.harness.engine.pms.audits.events.TriggeredInfo;
import io.harness.outbox.OutboxEvent;
import io.harness.pms.events.PipelineCreateEvent;
import io.harness.pms.events.PipelineDeleteEvent;
import io.harness.pms.events.PipelineOutboxEvents;
import io.harness.pms.events.PipelineUpdateEvent;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextData;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.util.Charsets;
import com.google.common.io.Resources;
import io.serializer.HObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineOutboxEventHandlerTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private AuditClientService auditClientService;
  private PipelineOutboxEventHandler eventHandler;
  String newYaml;
  String oldYaml;

  @Before
  public void setup() throws IOException {
    objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
    auditClientService = mock(AuditClientService.class);
    eventHandler = spy(new PipelineOutboxEventHandler(auditClientService, null));
    newYaml = Resources.toString(this.getClass().getClassLoader().getResource("pipeline.yml"), Charsets.UTF_8);
    oldYaml =
        Resources.toString(this.getClass().getClassLoader().getResource("pipeline-extensive.yml"), Charsets.UTF_8);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCreate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    PipelineEntity pipeline =
        PipelineEntity.builder().name(randomAlphabetic(10)).identifier(identifier).yaml(newYaml).build();
    PipelineCreateEvent pipelineCreateEvent =
        new PipelineCreateEvent(accountIdentifier, orgIdentifier, projectIdentifier, pipeline);
    String eventData = objectMapper.writeValueAsString(pipelineCreateEvent);
    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .resource(pipelineCreateEvent.getResource())
                                  .resourceScope(pipelineCreateEvent.getResourceScope())
                                  .eventType(PipelineOutboxEvents.PIPELINE_CREATED)
                                  .blocked(false)
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .eventData(eventData)
                                  .id(randomAlphabetic(10))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    eventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.CREATE, auditEntry.getAction());
    assertNull(auditEntry.getOldYaml());
    assertEquals(newYaml, auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    PipelineEntity newPipeline =
        PipelineEntity.builder().name(randomAlphabetic(10)).identifier(identifier).yaml(newYaml).build();
    PipelineEntity oldPipeline =
        PipelineEntity.builder().name(randomAlphabetic(10)).identifier(identifier).yaml(oldYaml).build();
    PipelineUpdateEvent pipelineUpdateEvent =
        new PipelineUpdateEvent(accountIdentifier, orgIdentifier, projectIdentifier, newPipeline, oldPipeline);
    String eventData = objectMapper.writeValueAsString(pipelineUpdateEvent);
    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .resource(pipelineUpdateEvent.getResource())
                                  .resourceScope(pipelineUpdateEvent.getResourceScope())
                                  .eventType(PipelineOutboxEvents.PIPELINE_UPDATED)
                                  .blocked(false)
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .eventData(eventData)
                                  .id(randomAlphabetic(10))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    eventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
    assertEquals(newYaml, auditEntry.getNewYaml());
    assertEquals(oldYaml, auditEntry.getOldYaml());
  }

  // a bug was introduced where accidentally some events have old pipeline as null. Handing it here to ensure that those
  // events are not audited and don't throw a Null Pointer Exception.
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateWithOldPipelineAsNull() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    PipelineEntity newPipeline =
        PipelineEntity.builder().name(randomAlphabetic(10)).identifier(identifier).yaml(newYaml).build();
    PipelineUpdateEvent pipelineUpdateEvent =
        new PipelineUpdateEvent(accountIdentifier, orgIdentifier, projectIdentifier, newPipeline, null);
    String eventData = objectMapper.writeValueAsString(pipelineUpdateEvent);
    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .resource(pipelineUpdateEvent.getResource())
                                  .resourceScope(pipelineUpdateEvent.getResourceScope())
                                  .eventType(PipelineOutboxEvents.PIPELINE_UPDATED)
                                  .blocked(false)
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .eventData(eventData)
                                  .id(randomAlphabetic(10))
                                  .build();
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    eventHandler.handle(outboxEvent);
    verify(auditClientService, times(0)).publishAudit(any(), any(), any());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testDelete() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    PipelineEntity pipeline =
        PipelineEntity.builder().name(randomAlphabetic(10)).identifier(identifier).yaml(oldYaml).build();
    PipelineDeleteEvent pipelineDeleteEvent =
        new PipelineDeleteEvent(accountIdentifier, orgIdentifier, projectIdentifier, pipeline);
    String eventData = objectMapper.writeValueAsString(pipelineDeleteEvent);
    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .resource(pipelineDeleteEvent.getResource())
                                  .resourceScope(pipelineDeleteEvent.getResourceScope())
                                  .eventType(PipelineOutboxEvents.PIPELINE_DELETED)
                                  .blocked(false)
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .eventData(eventData)
                                  .id(randomAlphabetic(10))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    eventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.DELETE, auditEntry.getAction());
    assertNull(auditEntry.getNewYaml());
    assertEquals(oldYaml, auditEntry.getOldYaml());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testAbort() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);

    TriggeredInfo triggeredInfo =
        TriggeredInfo.builder().identifier("admin").extraInfo(Map.of("email", "email@em.com")).build();

    PipelineAbortEvent pipelineAbortEvent = new PipelineAbortEvent(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, "planExecutionId", triggeredInfo, 0);
    String eventData = objectMapper.writeValueAsString(pipelineAbortEvent);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .resource(pipelineAbortEvent.getResource())
                                  .resourceScope(pipelineAbortEvent.getResourceScope())
                                  .eventType(NodeExecutionOutboxEventConstants.PIPELINE_ABORT)
                                  .blocked(false)
                                  .globalContext(new GlobalContext())
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .eventData(eventData)
                                  .id(randomAlphabetic(10))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    eventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.ABORT, auditEntry.getAction());
    assertEquals("email@em.com",
        ((NodeExecutionEventData) auditEntry.getAuditEventData()).getTriggeredBy().getExtraInfo().get("email"));
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testStartPipeline() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);

    TriggeredInfo triggeredInfo =
        TriggeredInfo.builder().identifier("admin").extraInfo(Map.of("email", "email@em.com")).build();

    PipelineStartEvent pipelineStartEvent = new PipelineStartEvent(accountIdentifier, orgIdentifier, projectIdentifier,
        identifier, "planExecutionId", triggeredInfo, Instant.now().toEpochMilli(), 0);
    String eventData = objectMapper.writeValueAsString(pipelineStartEvent);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .resource(pipelineStartEvent.getResource())
                                  .resourceScope(pipelineStartEvent.getResourceScope())
                                  .eventType(NodeExecutionOutboxEventConstants.PIPELINE_START)
                                  .blocked(false)
                                  .globalContext(new GlobalContext())
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .eventData(eventData)
                                  .id(randomAlphabetic(10))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    eventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.START, auditEntry.getAction());
    assertEquals("email@em.com",
        ((NodeExecutionEventData) auditEntry.getAuditEventData()).getTriggeredBy().getExtraInfo().get("email"));
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testEndPipeline() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);

    TriggeredInfo triggeredInfo =
        TriggeredInfo.builder().identifier("admin").extraInfo(Map.of("email", "email@em.com")).build();

    PipelineEndEvent pipelineEndEvent =
        new PipelineEndEvent(accountIdentifier, orgIdentifier, projectIdentifier, identifier, "planExecutionId",
            triggeredInfo, "RUNNING", Instant.now().toEpochMilli() - 60, Instant.now().toEpochMilli(), 1);
    String eventData = objectMapper.writeValueAsString(pipelineEndEvent);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .resource(pipelineEndEvent.getResource())
                                  .resourceScope(pipelineEndEvent.getResourceScope())
                                  .eventType(NodeExecutionOutboxEventConstants.PIPELINE_END)
                                  .blocked(false)
                                  .globalContext(new GlobalContext())
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .eventData(eventData)
                                  .id(randomAlphabetic(10))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    eventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.END, auditEntry.getAction());
    assertEquals("email@em.com",
        ((NodeExecutionEventData) auditEntry.getAuditEventData()).getTriggeredBy().getExtraInfo().get("email"));
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testStartStage() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);

    TriggeredInfo triggeredInfo =
        TriggeredInfo.builder().identifier("admin").extraInfo(Map.of("email", "email@em.com")).build();

    StageStartEvent stageStartEvent =
        new StageStartEvent(accountIdentifier, orgIdentifier, projectIdentifier, identifier, "planExecutionId",
            "stageIdentifier", "Deploy", Instant.now().toEpochMilli(), "nodeExecutionId", 1, triggeredInfo);
    String eventData = objectMapper.writeValueAsString(stageStartEvent);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .resource(stageStartEvent.getResource())
                                  .resourceScope(stageStartEvent.getResourceScope())
                                  .eventType(NodeExecutionOutboxEventConstants.STAGE_START)
                                  .blocked(false)
                                  .globalContext(new GlobalContext())
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .eventData(eventData)
                                  .id(randomAlphabetic(10))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    eventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.STAGE_START, auditEntry.getAction());
    assertEquals("email@em.com",
        ((NodeExecutionEventData) auditEntry.getAuditEventData()).getTriggeredBy().getExtraInfo().get("email"));
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testEndStage() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);

    TriggeredInfo triggeredInfo =
        TriggeredInfo.builder().identifier("admin").extraInfo(Map.of("email", "email@em.com")).build();

    StageEndEvent stageEndEvent = new StageEndEvent(accountIdentifier, orgIdentifier, projectIdentifier, identifier,
        "planExecutionId", "stageIdentifier", "Deploy", Instant.now().toEpochMilli() - 10, "nodeExecutionId",
        Instant.now().toEpochMilli(), "RUNNING", 1, triggeredInfo);
    String eventData = objectMapper.writeValueAsString(stageEndEvent);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .resource(stageEndEvent.getResource())
                                  .resourceScope(stageEndEvent.getResourceScope())
                                  .eventType(NodeExecutionOutboxEventConstants.STAGE_END)
                                  .blocked(false)
                                  .globalContext(new GlobalContext())
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .eventData(eventData)
                                  .id(randomAlphabetic(10))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    eventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.STAGE_END, auditEntry.getAction());
    assertEquals("email@em.com",
        ((NodeExecutionEventData) auditEntry.getAuditEventData()).getTriggeredBy().getExtraInfo().get("email"));
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testGetIdentifierForPrincipal() {
    TriggeredInfo triggeredInfo =
        TriggeredInfo.builder().identifier("admin").extraInfo(Map.of("email", "email@em.com")).build();

    NodeExecutionEventData nodeExecutionEventData = buildNodeExecutionEventData(triggeredInfo);
    String identifierForPrincipal = eventHandler.getIdentifierForPrincipal(nodeExecutionEventData);
    assertEquals(identifierForPrincipal, "email@em.com");

    TriggeredInfo triggeredInfo1 = TriggeredInfo.builder().identifier("admin").extraInfo(Map.of("email", "")).build();

    nodeExecutionEventData = buildNodeExecutionEventData(triggeredInfo1);

    String identifierForPrincipal1 = eventHandler.getIdentifierForPrincipal(nodeExecutionEventData);
    assertEquals(identifierForPrincipal1, "admin");
  }

  private void assertAuditEntry(String accountId, String orgIdentifier, String projectIdentifier, String identifier,
      AuditEntry auditEntry, OutboxEvent outboxEvent) {
    assertNotNull(auditEntry);
    assertEquals(accountId, auditEntry.getResourceScope().getAccountIdentifier());
    assertEquals(orgIdentifier, auditEntry.getResourceScope().getOrgIdentifier());
    assertEquals(projectIdentifier, auditEntry.getResourceScope().getProjectIdentifier());
    assertEquals(auditEntry.getInsertId(), outboxEvent.getId());
    assertEquals(identifier, auditEntry.getResource().getIdentifier());
    assertEquals(ModuleType.PMS, auditEntry.getModule());
    assertEquals(outboxEvent.getCreatedAt().longValue(), auditEntry.getTimestamp());
  }

  private NodeExecutionEventData buildNodeExecutionEventData(TriggeredInfo triggeredInfo) {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String planExecutionId = randomAlphabetic(10);

    return NodeExecutionEventData.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .pipelineIdentifier(identifier)
        .planExecutionId(planExecutionId)
        .triggeredBy(TriggeredByInfoAuditDetails.builder()
                         .type(triggeredInfo.getType())
                         .identifier(triggeredInfo.getIdentifier())
                         .extraInfo(triggeredInfo.getExtraInfo())
                         .build())
        .build();
  }
}

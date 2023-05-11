/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.outbox;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.client.api.AuditClientService;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.events.TriggerCreateEvent;
import io.harness.ngtriggers.events.TriggerDeleteEvent;
import io.harness.ngtriggers.events.TriggerOutboxEvents;
import io.harness.ngtriggers.events.TriggerUpdateEvent;
import io.harness.ngtriggers.outbox.TriggerOutboxEventHandler;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.pms.events.InputSetCreateEvent;
import io.harness.pms.events.PipelineCreateEvent;
import io.harness.pms.events.PipelineOutboxEvents;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.pipeline.PipelineEntity;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

public class PMSOutboxEventHandlerTest {
  private PMSOutboxEventHandler pmsOutboxEventHandler;
  private Map<String, OutboxEventHandler> outboxEventHandlerMap = new HashMap<>();
  private AuditClientService auditClientService;
  private PMSInputSetService inputSetService;
  private String triggerNewYaml;
  private String triggerOldYaml;
  private String pipelineNewYaml;
  private String pipelineOldYaml;
  private String inputsetNewYaml;
  private String inputsetOldYaml;

  @Before
  public void setup() throws Exception {
    triggerNewYaml =
        Resources.toString(this.getClass().getClassLoader().getResource("trigger_new.yml"), Charsets.UTF_8);
    triggerOldYaml =
        Resources.toString(this.getClass().getClassLoader().getResource("trigger_old.yml"), Charsets.UTF_8);
    pipelineNewYaml = Resources.toString(this.getClass().getClassLoader().getResource("pipeline.yml"), Charsets.UTF_8);
    pipelineOldYaml =
        Resources.toString(this.getClass().getClassLoader().getResource("pipeline-extensive.yml"), Charsets.UTF_8);
    inputsetNewYaml = Resources.toString(this.getClass().getClassLoader().getResource("inputSet1.yml"), Charsets.UTF_8);
    inputsetOldYaml = Resources.toString(this.getClass().getClassLoader().getResource("inputSet2.yml"), Charsets.UTF_8);

    auditClientService = mock(AuditClientService.class);
    inputSetService = mock(PMSInputSetService.class);
    TriggerOutboxEventHandler triggerOutboxEventHandler = new TriggerOutboxEventHandler(auditClientService);
    InputSetEventHandler inputSetEventHandler = new InputSetEventHandler(auditClientService, inputSetService);
    PipelineOutboxEventHandler pipelineOutboxEventHandler =
        new PipelineOutboxEventHandler(auditClientService, inputSetEventHandler);
    outboxEventHandlerMap.put("PIPELINE", pipelineOutboxEventHandler);
    outboxEventHandlerMap.put("INPUT_SET", pipelineOutboxEventHandler);
    outboxEventHandlerMap.put("TRIGGER", triggerOutboxEventHandler);
    pmsOutboxEventHandler = new PMSOutboxEventHandler(outboxEventHandlerMap);
  }

  @Test
  @Category(UnitTests.class)
  public void testTriggerCreate() throws Exception {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    NGTriggerEntity triggerEntity = NGTriggerEntity.builder()
                                        .name(randomAlphabetic(10))
                                        .identifier(identifier)
                                        .type(NGTriggerType.WEBHOOK)
                                        .yaml(triggerNewYaml)
                                        .build();
    TriggerCreateEvent triggerCreateEvent =
        new TriggerCreateEvent(accountIdentifier, orgIdentifier, projectIdentifier, triggerEntity);
    OutboxEvent outboxEvent = TestUtils.createOutboxEvent(triggerCreateEvent, TriggerOutboxEvents.TRIGGER_CREATED);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    assertEquals(true, pmsOutboxEventHandler.handle(outboxEvent));
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertEquals(Action.CREATE, auditEntry.getAction());
  }

  @Test
  @Category(UnitTests.class)
  public void testTriggerDelete() throws Exception {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    NGTriggerEntity triggerEntity = NGTriggerEntity.builder()
                                        .name(randomAlphabetic(10))
                                        .identifier(identifier)
                                        .type(NGTriggerType.WEBHOOK)
                                        .yaml(triggerOldYaml)
                                        .build();
    TriggerDeleteEvent triggerDeleteEvent =
        new TriggerDeleteEvent(accountIdentifier, orgIdentifier, projectIdentifier, triggerEntity);
    OutboxEvent outboxEvent = TestUtils.createOutboxEvent(triggerDeleteEvent, TriggerOutboxEvents.TRIGGER_DELETED);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    assertEquals(true, pmsOutboxEventHandler.handle(outboxEvent));
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertEquals(Action.DELETE, auditEntry.getAction());
  }

  @Test
  @Category(UnitTests.class)
  public void testTriggerUpdate() throws Exception {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    NGTriggerEntity newTrigger = NGTriggerEntity.builder()
                                     .name(randomAlphabetic(10))
                                     .identifier(identifier)
                                     .type(NGTriggerType.WEBHOOK)
                                     .yaml(triggerNewYaml)
                                     .build();
    NGTriggerEntity oldTrigger = NGTriggerEntity.builder()
                                     .name(randomAlphabetic(10))
                                     .identifier(identifier)
                                     .type(NGTriggerType.SCHEDULED)
                                     .yaml(triggerOldYaml)
                                     .build();
    TriggerUpdateEvent triggerUpdateEvent =
        new TriggerUpdateEvent(accountIdentifier, orgIdentifier, projectIdentifier, oldTrigger, newTrigger);
    OutboxEvent outboxEvent = TestUtils.createOutboxEvent(triggerUpdateEvent, TriggerOutboxEvents.TRIGGER_UPDATED);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    assertEquals(true, pmsOutboxEventHandler.handle(outboxEvent));
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertEquals(Action.UPDATE, auditEntry.getAction());
  }

  @Test
  @Category(UnitTests.class)
  public void testPipelineCreate() throws Exception {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    PipelineEntity pipeline =
        PipelineEntity.builder().name(randomAlphabetic(10)).identifier(identifier).yaml(pipelineNewYaml).build();
    PipelineCreateEvent pipelineCreateEvent =
        new PipelineCreateEvent(accountIdentifier, orgIdentifier, projectIdentifier, pipeline);
    OutboxEvent outboxEvent = TestUtils.createOutboxEvent(pipelineCreateEvent, PipelineOutboxEvents.PIPELINE_CREATED);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    assertEquals(true, pmsOutboxEventHandler.handle(outboxEvent));
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertEquals(Action.CREATE, auditEntry.getAction());
  }

  @Test
  @Category(UnitTests.class)
  public void testInputSetCreate() throws Exception {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String pipelineIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    InputSetEntity inputSet =
        InputSetEntity.builder().name(randomAlphabetic(10)).identifier(identifier).yaml(inputsetNewYaml).build();
    InputSetCreateEvent inputSetCreateEvent = new InputSetCreateEvent(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, inputSet, false);
    OutboxEvent outboxEvent = TestUtils.createOutboxEvent(inputSetCreateEvent, PipelineOutboxEvents.INPUT_SET_CREATED);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    assertEquals(true, pmsOutboxEventHandler.handle(outboxEvent));
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertEquals(Action.CREATE, auditEntry.getAction());
  }

  @Test
  @Category(UnitTests.class)
  public void testRandomEvent() throws Exception {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    PipelineEntity pipeline =
        PipelineEntity.builder().name(randomAlphabetic(10)).identifier(identifier).yaml(pipelineNewYaml).build();
    PipelineCreateEvent pipelineCreateEvent =
        new PipelineCreateEvent(accountIdentifier, orgIdentifier, projectIdentifier, pipeline);
    OutboxEvent outboxEvent = TestUtils.createOutboxEvent(pipelineCreateEvent, "RANDOM");
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    assertEquals(false, pmsOutboxEventHandler.handle(outboxEvent));
  }
}

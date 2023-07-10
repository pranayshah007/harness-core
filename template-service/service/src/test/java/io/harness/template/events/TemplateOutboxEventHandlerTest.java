/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.events;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.client.api.AuditClientService;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.encryption.Scope;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextData;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.helpers.TemplateReferenceHelper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.util.Charsets;
import com.google.common.io.Resources;
import io.serializer.HObjectMapper;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(CDC)
public class TemplateOutboxEventHandlerTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private AuditClientService auditClientService;
  private Producer eventProducer;
  private TemplateOutboxEventHandler templateOutboxEventHandler;
  private TemplateReferenceHelper templateReferenceHelper;
  String newYaml;
  String oldYaml;

  @Before
  public void setup() throws IOException {
    objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
    auditClientService = mock(AuditClientService.class);
    eventProducer = mock(Producer.class);
    templateReferenceHelper = mock(TemplateReferenceHelper.class);
    templateOutboxEventHandler =
        spy(new TemplateOutboxEventHandler(auditClientService, eventProducer, templateReferenceHelper));
    newYaml = Resources.toString(this.getClass().getClassLoader().getResource("template.yaml"), Charsets.UTF_8);
    oldYaml = Resources.toString(this.getClass().getClassLoader().getResource("template_updated.yaml"), Charsets.UTF_8);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testCreate() throws IOException, ClassNotFoundException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String templateVersionLabel = randomAlphabetic(10);

    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .name(randomAlphabetic(10))
                                        .identifier(identifier)
                                        .versionLabel(templateVersionLabel)
                                        .templateScope(Scope.PROJECT)
                                        .yaml(newYaml)
                                        .templateEntityType(TemplateEntityType.STAGE_TEMPLATE)
                                        .build();
    TemplateCreateEvent createEvent =
        new TemplateCreateEvent(accountIdentifier, orgIdentifier, projectIdentifier, templateEntity, "");
    String eventData = objectMapper.writeValueAsString(createEvent);
    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .resource(createEvent.getResource())
                                  .resourceScope(createEvent.getResourceScope())
                                  .eventType(TemplateOutboxEvents.TEMPLATE_VERSION_CREATED)
                                  .blocked(false)
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .eventData(eventData)
                                  .id(randomAlphabetic(10))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    templateOutboxEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    verify(eventProducer, times(1)).send(messageArgumentCaptor.capture());

    assertRedisEvent(messageArgumentCaptor.getAllValues().get(0), EventsFrameworkMetadataConstants.CREATE_ACTION);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, templateVersionLabel, auditEntry, outboxEvent);
    assertEquals(Action.CREATE, auditEntry.getAction());
    assertNull(auditEntry.getOldYaml());
    assertEquals(newYaml, auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testUpdate() throws IOException, ClassNotFoundException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String templateVersionLabel = randomAlphabetic(10);

    TemplateEntity oldTemplateEntity = TemplateEntity.builder()
                                           .name(randomAlphabetic(10))
                                           .identifier(identifier)
                                           .versionLabel(templateVersionLabel)
                                           .templateScope(Scope.PROJECT)
                                           .yaml(oldYaml)
                                           .templateEntityType(TemplateEntityType.STAGE_TEMPLATE)
                                           .build();
    TemplateEntity newTemplateEntity = TemplateEntity.builder()
                                           .name(randomAlphabetic(10))
                                           .identifier(identifier)
                                           .versionLabel(templateVersionLabel)
                                           .templateScope(Scope.PROJECT)
                                           .templateEntityType(TemplateEntityType.STAGE_TEMPLATE)
                                           .yaml(newYaml)
                                           .build();
    TemplateUpdateEvent updateEvent = new TemplateUpdateEvent(accountIdentifier, orgIdentifier, projectIdentifier,
        newTemplateEntity, oldTemplateEntity, "", TemplateUpdateEventType.OTHERS_EVENT);
    String eventData = objectMapper.writeValueAsString(updateEvent);
    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .resource(updateEvent.getResource())
                                  .resourceScope(updateEvent.getResourceScope())
                                  .eventType(TemplateOutboxEvents.TEMPLATE_VERSION_UPDATED)
                                  .blocked(false)
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .eventData(eventData)
                                  .id(randomAlphabetic(10))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    templateOutboxEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    verify(eventProducer, times(1)).send(messageArgumentCaptor.capture());

    assertRedisEvent(messageArgumentCaptor.getAllValues().get(0), EventsFrameworkMetadataConstants.UPDATE_ACTION);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, templateVersionLabel, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
    assertEquals(newYaml, auditEntry.getNewYaml());
    assertEquals(oldYaml, auditEntry.getOldYaml());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDelete() throws IOException, ClassNotFoundException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String templateVersionLabel = randomAlphabetic(10);

    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .name(randomAlphabetic(10))
                                        .identifier(identifier)
                                        .versionLabel(templateVersionLabel)
                                        .templateScope(Scope.PROJECT)
                                        .templateEntityType(TemplateEntityType.STAGE_TEMPLATE)
                                        .isEntityInvalid(false)
                                        .yaml(oldYaml)
                                        .build();
    TemplateDeleteEvent deleteEvent =
        new TemplateDeleteEvent(accountIdentifier, orgIdentifier, projectIdentifier, templateEntity, "");
    String eventData = objectMapper.writeValueAsString(deleteEvent);
    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .resource(deleteEvent.getResource())
                                  .resourceScope(deleteEvent.getResourceScope())
                                  .eventType(TemplateOutboxEvents.TEMPLATE_VERSION_DELETED)
                                  .blocked(false)
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .eventData(eventData)
                                  .id(randomAlphabetic(10))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    templateOutboxEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());
    verify(templateReferenceHelper, times(1)).deleteTemplateReferences(templateEntity);

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    verify(eventProducer, times(1)).send(messageArgumentCaptor.capture());

    assertRedisEvent(messageArgumentCaptor.getAllValues().get(0), EventsFrameworkMetadataConstants.DELETE_ACTION);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, templateVersionLabel, auditEntry, outboxEvent);
    assertEquals(Action.DELETE, auditEntry.getAction());
    assertNull(auditEntry.getNewYaml());
    assertEquals(oldYaml, auditEntry.getOldYaml());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testForceDelete() throws IOException, ClassNotFoundException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String templateVersionLabel = randomAlphabetic(10);

    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .name(randomAlphabetic(10))
                                        .identifier(identifier)
                                        .versionLabel(templateVersionLabel)
                                        .templateScope(Scope.PROJECT)
                                        .templateEntityType(TemplateEntityType.STAGE_TEMPLATE)
                                        .isEntityInvalid(false)
                                        .yaml(oldYaml)
                                        .build();
    TemplateForceDeleteEvent deleteEvent =
        new TemplateForceDeleteEvent(accountIdentifier, orgIdentifier, projectIdentifier, templateEntity, "");
    String eventData = objectMapper.writeValueAsString(deleteEvent);
    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .resource(deleteEvent.getResource())
                                  .resourceScope(deleteEvent.getResourceScope())
                                  .eventType(TemplateOutboxEvents.TEMPLATE_VERSION_FORCE_DELETED)
                                  .blocked(false)
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .eventData(eventData)
                                  .id(randomAlphabetic(10))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    templateOutboxEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());
    verify(templateReferenceHelper, times(1)).deleteTemplateReferences(templateEntity);

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    verify(eventProducer, times(1)).send(messageArgumentCaptor.capture());

    assertRedisEvent(messageArgumentCaptor.getAllValues().get(0), EventsFrameworkMetadataConstants.DELETE_ACTION);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, templateVersionLabel, auditEntry, outboxEvent);
    assertEquals(Action.FORCE_DELETE, auditEntry.getAction());
    assertNull(auditEntry.getNewYaml());
    assertEquals(oldYaml, auditEntry.getOldYaml());
  }

  private void assertAuditEntry(String accountId, String orgIdentifier, String projectIdentifier, String identifier,
      String templateVersionLabel, AuditEntry auditEntry, OutboxEvent outboxEvent) {
    assertNotNull(auditEntry);
    assertEquals(accountId, auditEntry.getResourceScope().getAccountIdentifier());
    assertEquals(orgIdentifier, auditEntry.getResourceScope().getOrgIdentifier());
    assertEquals(projectIdentifier, auditEntry.getResourceScope().getProjectIdentifier());
    assertEquals(auditEntry.getInsertId(), outboxEvent.getId());
    assertEquals(identifier, auditEntry.getResource().getIdentifier());
    assertEquals(templateVersionLabel, auditEntry.getResource().getLabels().get("versionLabel"));
    assertEquals(ModuleType.TEMPLATESERVICE, auditEntry.getModule());
    assertEquals(outboxEvent.getCreatedAt().longValue(), auditEntry.getTimestamp());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testUpdateScope() throws ClassNotFoundException, IOException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String templateVersionLabel = randomAlphabetic(10);

    TemplateEntity oldTemplateEntity = TemplateEntity.builder()
                                           .name(randomAlphabetic(10))
                                           .identifier(identifier)
                                           .versionLabel(templateVersionLabel)
                                           .accountId(accountIdentifier)
                                           .orgIdentifier(orgIdentifier)
                                           .projectIdentifier(projectIdentifier)
                                           .templateScope(Scope.PROJECT)
                                           .templateEntityType(TemplateEntityType.STAGE_TEMPLATE)
                                           .yaml(oldYaml)
                                           .build();
    TemplateEntity newTemplateEntity = TemplateEntity.builder()
                                           .name(randomAlphabetic(10))
                                           .identifier(identifier)
                                           .versionLabel(templateVersionLabel)
                                           .accountId(accountIdentifier)
                                           .orgIdentifier(orgIdentifier)
                                           .templateScope(Scope.ORG)
                                           .templateEntityType(TemplateEntityType.STAGE_TEMPLATE)
                                           .yaml(newYaml)
                                           .build();

    TemplateUpdateEvent updateEvent = new TemplateUpdateEvent(accountIdentifier, orgIdentifier, projectIdentifier,
        newTemplateEntity, oldTemplateEntity, "", TemplateUpdateEventType.TEMPLATE_CHANGE_SCOPE_EVENT);
    String eventData = objectMapper.writeValueAsString(updateEvent);
    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .resource(updateEvent.getResource())
                                  .resourceScope(updateEvent.getResourceScope())
                                  .eventType(TemplateOutboxEvents.TEMPLATE_VERSION_UPDATED)
                                  .blocked(false)
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .eventData(eventData)
                                  .id(randomAlphabetic(10))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    templateOutboxEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    verify(eventProducer, times(2)).send(messageArgumentCaptor.capture());

    assertRedisEvent(messageArgumentCaptor.getAllValues().get(0), EventsFrameworkMetadataConstants.DELETE_ACTION);
    assertRedisEvent(messageArgumentCaptor.getAllValues().get(1), EventsFrameworkMetadataConstants.CREATE_ACTION);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, null, identifier, templateVersionLabel, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
    assertEquals(newYaml, auditEntry.getNewYaml());
    assertEquals(oldYaml, auditEntry.getOldYaml());
  }

  private void assertRedisEvent(Message message, String action) {
    assertEquals(message.getMetadataOrThrow(EventsFrameworkMetadataConstants.ENTITY_TYPE), "TEMPLATE");
    assertEquals(message.getMetadataOrThrow(EventsFrameworkMetadataConstants.ACTION), action);
    assertNotNull(message.getData());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testPublishEvent() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String templateVersionLabel = randomAlphabetic(10);

    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .name(randomAlphabetic(10))
                                        .identifier(identifier)
                                        .versionLabel(templateVersionLabel)
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .templateScope(Scope.PROJECT)
                                        .isStableTemplate(true)
                                        .templateEntityType(TemplateEntityType.STAGE_TEMPLATE)
                                        .yaml(oldYaml)
                                        .build();

    TemplateCreateEvent createEvent =
        new TemplateCreateEvent(accountIdentifier, orgIdentifier, projectIdentifier, templateEntity, "");
    GlobalContext globalContext = new GlobalContext();
    String eventData = objectMapper.writeValueAsString(createEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .resource(createEvent.getResource())
                                  .resourceScope(createEvent.getResourceScope())
                                  .eventType(TemplateOutboxEvents.TEMPLATE_VERSION_UPDATED)
                                  .blocked(false)
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .eventData(eventData)
                                  .id(randomAlphabetic(10))
                                  .build();

    ArgumentCaptor<EntityChangeDTO> entityChangeDTOArgumentCaptor = ArgumentCaptor.forClass(EntityChangeDTO.class);
    templateOutboxEventHandler.publishEvent(
        outboxEvent, EventsFrameworkMetadataConstants.UPDATE_ACTION, templateEntity);
    verify(templateOutboxEventHandler)
        .publishEvent(anyString(), anyString(), anyString(), entityChangeDTOArgumentCaptor.capture());
    assertEquals(entityChangeDTOArgumentCaptor.getValue().getIdentifier().getValue(), identifier);
    assertEquals(entityChangeDTOArgumentCaptor.getValue().getProjectIdentifier().getValue(), projectIdentifier);
    assertEquals(entityChangeDTOArgumentCaptor.getValue().getOrgIdentifier().getValue(), orgIdentifier);
    assertEquals(entityChangeDTOArgumentCaptor.getValue().getAccountIdentifier().getValue(), accountIdentifier);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testPublishEventWhenEventsFrameWorkIsDown() {
    doThrow(new EventsFrameworkDownException("msg")).when(eventProducer).send(any());
    boolean published = templateOutboxEventHandler.publishEvent(EventsFrameworkMetadataConstants.UPDATE_ACTION,
        "accountIdentifier", "identifer", EntityChangeDTO.newBuilder().build());
    assertFalse(published);
  }
}

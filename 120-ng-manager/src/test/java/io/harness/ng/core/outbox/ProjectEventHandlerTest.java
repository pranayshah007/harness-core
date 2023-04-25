/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.RESTORE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;
import static io.harness.rule.OwnerRule.KARAN;

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
import io.harness.context.GlobalContext;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectRequest;
import io.harness.ng.core.events.ProjectCreateEvent;
import io.harness.ng.core.events.ProjectDeleteEvent;
import io.harness.ng.core.events.ProjectRestoreEvent;
import io.harness.ng.core.events.ProjectUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextData;
import io.harness.security.dto.UserPrincipal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(PL)
public class ProjectEventHandlerTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private Producer producer;
  private AuditClientService auditClientService;
  private ProjectEventHandler projectEventHandler;

  @Before
  public void setup() {
    objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    producer = mock(Producer.class);
    auditClientService = mock(AuditClientService.class);
    projectEventHandler = spy(new ProjectEventHandler(producer, auditClientService));
  }

  private ProjectDTO getProjectDTO(String orgIdentifier, String identifier) {
    List<ModuleType> moduleTypes = new ArrayList<>();
    moduleTypes.add(ModuleType.CD);

    return ProjectDTO.builder().orgIdentifier(orgIdentifier).identifier(identifier).name(randomAlphabetic(10)).build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ProjectDTO projectDTO = getProjectDTO(orgIdentifier, identifier);
    ProjectCreateEvent projectCreateEvent = new ProjectCreateEvent(accountIdentifier, projectDTO);
    String eventData = objectMapper.writeValueAsString(projectCreateEvent);
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
                                  .eventType("ProjectCreated")
                                  .globalContext(globalContext)
                                  .eventData(eventData)
                                  .resourceScope(projectCreateEvent.getResourceScope())
                                  .resource(projectCreateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    String newYaml = getYamlString(ProjectRequest.builder().project(projectDTO).build());

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verifyMethodInvocation(outboxEvent, messageArgumentCaptor, auditEntryArgumentCaptor);

    Message message = messageArgumentCaptor.getValue();
    assertMessage(message, accountIdentifier, CREATE_ACTION);

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
    ProjectDTO oldProjectDTO = getProjectDTO(orgIdentifier, identifier);
    ProjectDTO newProjectDTO = getProjectDTO(orgIdentifier, identifier);
    ProjectUpdateEvent projectUpdateEvent = new ProjectUpdateEvent(accountIdentifier, newProjectDTO, oldProjectDTO);
    String eventData = objectMapper.writeValueAsString(projectUpdateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("ProjectUpdated")
                                  .eventData(eventData)
                                  .resourceScope(projectUpdateEvent.getResourceScope())
                                  .resource(projectUpdateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    String oldYaml = getYamlString(ProjectRequest.builder().project(oldProjectDTO).build());
    String newYaml = getYamlString(ProjectRequest.builder().project(newProjectDTO).build());

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verifyMethodInvocation(outboxEvent, messageArgumentCaptor, auditEntryArgumentCaptor);

    Message message = messageArgumentCaptor.getValue();
    assertMessage(message, accountIdentifier, UPDATE_ACTION);

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
    ProjectDTO projectDTO = getProjectDTO(orgIdentifier, identifier);
    ProjectDeleteEvent projectDeleteEvent = new ProjectDeleteEvent(accountIdentifier, projectDTO);
    String eventData = objectMapper.writeValueAsString(projectDeleteEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("ProjectDeleted")
                                  .eventData(eventData)
                                  .resourceScope(projectDeleteEvent.getResourceScope())
                                  .resource(projectDeleteEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    String oldYaml = getYamlString(ProjectRequest.builder().project(projectDTO).build());

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verifyMethodInvocation(outboxEvent, messageArgumentCaptor, auditEntryArgumentCaptor);

    Message message = messageArgumentCaptor.getValue();
    assertMessage(message, accountIdentifier, DELETE_ACTION);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.DELETE, auditEntry.getAction());
    assertNull(auditEntry.getNewYaml());
    assertEquals(oldYaml, auditEntry.getOldYaml());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testRestore() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ProjectDTO projectDTO = getProjectDTO(orgIdentifier, identifier);
    ProjectRestoreEvent projectRestoreEvent = new ProjectRestoreEvent(accountIdentifier, projectDTO);
    String eventData = objectMapper.writeValueAsString(projectRestoreEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("ProjectRestored")
                                  .eventData(eventData)
                                  .resourceScope(projectRestoreEvent.getResourceScope())
                                  .resource(projectRestoreEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    String newYaml = getYamlString(ProjectRequest.builder().project(projectDTO).build());

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verifyMethodInvocation(outboxEvent, messageArgumentCaptor, auditEntryArgumentCaptor);

    Message message = messageArgumentCaptor.getValue();
    assertMessage(message, accountIdentifier, RESTORE_ACTION);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.RESTORE, auditEntry.getAction());
    assertNull(auditEntry.getOldYaml());
    assertEquals(newYaml, auditEntry.getNewYaml());
  }

  private void verifyMethodInvocation(OutboxEvent outboxEvent, ArgumentCaptor<Message> messageArgumentCaptor,
      ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor) {
    when(producer.send(any())).thenReturn("");
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);

    projectEventHandler.handle(outboxEvent);

    verify(producer, times(1)).send(messageArgumentCaptor.capture());
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
  }

  private void assertMessage(Message message, String accountIdentifier, String action) {
    assertNotNull(message.getMetadataMap());
    Map<String, String> metadataMap = message.getMetadataMap();
    assertEquals(accountIdentifier, metadataMap.get("accountId"));
    assertEquals(PROJECT_ENTITY, metadataMap.get(ENTITY_TYPE));
    assertEquals(action, metadataMap.get(ACTION));
  }

  private void assertAuditEntry(String accountIdentifier, String orgIdentifier, String identifier,
      AuditEntry auditEntry, OutboxEvent outboxEvent) {
    assertNotNull(auditEntry);
    assertEquals(outboxEvent.getId(), auditEntry.getInsertId());
    assertEquals(ResourceTypeConstants.PROJECT, auditEntry.getResource().getType());
    assertEquals(identifier, auditEntry.getResource().getIdentifier());
    assertEquals(accountIdentifier, auditEntry.getResourceScope().getAccountIdentifier());
    assertEquals(orgIdentifier, auditEntry.getResourceScope().getOrgIdentifier());
    assertEquals(identifier, auditEntry.getResourceScope().getProjectIdentifier());
    assertEquals(ModuleType.CORE, auditEntry.getModule());
    assertEquals(outboxEvent.getCreatedAt().longValue(), auditEntry.getTimestamp());
    assertNull(auditEntry.getEnvironment());
  }
}

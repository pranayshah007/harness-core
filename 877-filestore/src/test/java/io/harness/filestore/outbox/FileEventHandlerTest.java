/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.outbox;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;
import static io.harness.rule.OwnerRule.IVAN;

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
import io.harness.filestore.events.FileCreateEvent;
import io.harness.filestore.events.FileDeleteEvent;
import io.harness.filestore.events.FileUpdateEvent;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.ng.core.filestore.dto.FileStoreRequest;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(CDP)
public class FileEventHandlerTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private Producer eventProducer;
  private AuditClientService auditClientService;
  private FileEventHandler fileEventHandler;

  @Before
  public void setup() {
    objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    eventProducer = mock(Producer.class);
    auditClientService = mock(AuditClientService.class);
    fileEventHandler = spy(new FileEventHandler(auditClientService, eventProducer));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testCreate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    FileDTO fileDTO = getFileDTO(orgIdentifier, identifier);
    FileCreateEvent fileCreateEvent = new FileCreateEvent(accountIdentifier, fileDTO);
    String eventData = objectMapper.writeValueAsString(fileCreateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("FileCreated")
                                  .eventData(eventData)
                                  .resourceScope(fileCreateEvent.getResourceScope())
                                  .resource(fileCreateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .globalContext(new GlobalContext())
                                  .build();

    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    fileEventHandler.handle(outboxEvent);

    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());
    verify(eventProducer, times(1)).send(messageArgumentCaptor.capture());

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.CREATE, auditEntry.getAction());
    assertNull(auditEntry.getOldYaml());

    String newYaml = getYamlString(FileStoreRequest.builder().file(fileDTO).build());
    assertEquals(newYaml, auditEntry.getNewYaml());

    Message message = messageArgumentCaptor.getValue();
    assertMessage(message, accountIdentifier, "create");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testUpdate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    FileDTO oldFileDTO = getFileDTO(orgIdentifier, identifier);
    FileDTO newFileDTO = getFileDTO(orgIdentifier, identifier);
    FileUpdateEvent secretUpdateEvent = new FileUpdateEvent(accountIdentifier, newFileDTO, oldFileDTO);
    String eventData = objectMapper.writeValueAsString(secretUpdateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("FileUpdated")
                                  .eventData(eventData)
                                  .resourceScope(secretUpdateEvent.getResourceScope())
                                  .resource(secretUpdateEvent.getResource())
                                  .globalContext(new GlobalContext())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    String oldYaml = getYamlString(FileStoreRequest.builder().file(oldFileDTO).build());
    String newYaml = getYamlString(FileStoreRequest.builder().file(newFileDTO).build());

    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    fileEventHandler.handle(outboxEvent);

    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());
    verify(eventProducer, times(1)).send(messageArgumentCaptor.capture());

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
    assertEquals(oldYaml, auditEntry.getOldYaml());
    assertEquals(newYaml, auditEntry.getNewYaml());

    Message message = messageArgumentCaptor.getValue();
    assertMessage(message, accountIdentifier, "update");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDelete() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    FileDTO fileDTO = getFileDTO(orgIdentifier, identifier);
    FileDeleteEvent fileDeleteEvent = new FileDeleteEvent(accountIdentifier, fileDTO);
    String eventData = objectMapper.writeValueAsString(fileDeleteEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("FileDeleted")
                                  .eventData(eventData)
                                  .resourceScope(fileDeleteEvent.getResourceScope())
                                  .resource(fileDeleteEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .globalContext(new GlobalContext())
                                  .build();

    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    fileEventHandler.handle(outboxEvent);

    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());
    verify(eventProducer, times(1)).send(messageArgumentCaptor.capture());

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.DELETE, auditEntry.getAction());
    assertNull(auditEntry.getNewYaml());
    String oldYaml = getYamlString(FileStoreRequest.builder().file(fileDTO).build());
    assertEquals(oldYaml, auditEntry.getOldYaml());

    Message message = messageArgumentCaptor.getValue();
    assertMessage(message, accountIdentifier, "delete");
  }

  private FileDTO getFileDTO(String orgIdentifier, String identifier) {
    return FileDTO.builder().orgIdentifier(orgIdentifier).identifier(identifier).name(randomAlphabetic(10)).build();
  }

  private void assertAuditEntry(String accountIdentifier, String orgIdentifier, String identifier,
      AuditEntry auditEntry, OutboxEvent outboxEvent) {
    assertNotNull(auditEntry);
    assertEquals(outboxEvent.getId(), auditEntry.getInsertId());
    assertEquals(ResourceTypeConstants.FILE, auditEntry.getResource().getType());
    assertEquals(identifier, auditEntry.getResource().getIdentifier());
    assertEquals(accountIdentifier, auditEntry.getResourceScope().getAccountIdentifier());
    assertEquals(orgIdentifier, auditEntry.getResourceScope().getOrgIdentifier());
    assertNull(auditEntry.getResourceScope().getProjectIdentifier());
    assertEquals(ModuleType.CORE, auditEntry.getModule());
    assertEquals(outboxEvent.getCreatedAt().longValue(), auditEntry.getTimestamp());
    assertNull(auditEntry.getEnvironment());
  }

  private void assertMessage(Message message, String accountIdentifier, String action) {
    assertNotNull(message);
    assertEquals(message.getMetadataMap().get("accountId"), accountIdentifier);
    assertEquals(message.getMetadataMap().get("entityType"), "file");
    assertEquals(message.getMetadataMap().get("action"), action);
  }
}

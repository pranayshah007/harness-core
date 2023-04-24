/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.ipallowlist.events.IPAllowlistConfigCreateEvent.IP_ALLOWLIST_CONFIG_CREATED;
import static io.harness.ipallowlist.events.IPAllowlistConfigDeleteEvent.IP_ALLOWLIST_CONFIG_DELETED;
import static io.harness.ipallowlist.events.IPAllowlistConfigUpdateEvent.IP_ALLOWLIST_CONFIG_UPDATED;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;
import static io.harness.rule.OwnerRule.MEENAKSHI;

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
import io.harness.ipallowlist.dto.IPAllowlistDTO;
import io.harness.ipallowlist.events.IPAllowlistConfigCreateEvent;
import io.harness.ipallowlist.events.IPAllowlistConfigDeleteEvent;
import io.harness.ipallowlist.events.IPAllowlistConfigUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextData;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.ng.v1.model.AllowedSourceType;
import io.harness.spec.server.ng.v1.model.IPAllowlistConfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class IPAllowlistConfigEventHandlerTest extends CategoryTest {
  private ObjectMapper objectMapper;
  @Mock private AuditClientService auditClientService;
  private IPAllowlistConfigEventHandler ipAllowlistConfigEventHandler;

  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  String ACCOUNT_ID = randomAlphabetic(10);
  String IP_ADDRESS = "1.2.3.4";
  String NAME = randomAlphabetic(10);
  String IDENTIFIER = randomAlphabetic(10);

  @Before
  public void setup() {
    objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    MockitoAnnotations.initMocks(this);
    ipAllowlistConfigEventHandler = new IPAllowlistConfigEventHandler(auditClientService);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testHandleIpAllowlistConfigCreateEvent() throws JsonProcessingException {
    IPAllowlistConfig ipAllowlistConfig = getIpAllowlistConfig();
    IPAllowlistConfigCreateEvent ipAllowlistConfigCreateEvent =
        new IPAllowlistConfigCreateEvent(ACCOUNT_ID, ipAllowlistConfig);
    String eventData = objectMapper.writeValueAsString(ipAllowlistConfigCreateEvent);
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
                                  .eventType(IP_ALLOWLIST_CONFIG_CREATED)
                                  .globalContext(globalContext)
                                  .eventData(eventData)
                                  .resourceScope(ipAllowlistConfigCreateEvent.getResourceScope())
                                  .resource(ipAllowlistConfigCreateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    String newYaml = getYamlString(IPAllowlistDTO.builder().ipAllowlistConfig(ipAllowlistConfig).build());

    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verifyMethodInvocation(outboxEvent, auditEntryArgumentCaptor);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(auditEntry, outboxEvent);
    assertEquals(Action.CREATE, auditEntry.getAction());
    assertNull(auditEntry.getOldYaml());
    assertEquals(newYaml, auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testHandle_notSupportedEventType() {
    String eventType = randomAlphabetic(10);
    OutboxEvent event = OutboxEvent.builder().eventType(eventType).build();
    exceptionRule.expect(InvalidArgumentsException.class);
    exceptionRule.expectMessage(String.format("Not supported event type %s", eventType));
    ipAllowlistConfigEventHandler.handle(event);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testUpdate() throws JsonProcessingException {
    IPAllowlistConfig oldIpAllowlistConfig = getIpAllowlistConfig();
    IPAllowlistConfig newIpAllowlistConfig = getIpAllowlistConfig();
    newIpAllowlistConfig.setEnabled(false);
    newIpAllowlistConfig.setDescription("Updated Description");
    IPAllowlistConfigUpdateEvent ipAllowlistConfigUpdateEvent =
        new IPAllowlistConfigUpdateEvent(ACCOUNT_ID, newIpAllowlistConfig, oldIpAllowlistConfig);
    String eventData = objectMapper.writeValueAsString(ipAllowlistConfigUpdateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType(IP_ALLOWLIST_CONFIG_UPDATED)
                                  .eventData(eventData)
                                  .resourceScope(ipAllowlistConfigUpdateEvent.getResourceScope())
                                  .resource(ipAllowlistConfigUpdateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    String oldYaml = getYamlString(IPAllowlistDTO.builder().ipAllowlistConfig(oldIpAllowlistConfig).build());
    String newYaml = getYamlString(IPAllowlistDTO.builder().ipAllowlistConfig(newIpAllowlistConfig).build());

    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verifyMethodInvocation(outboxEvent, auditEntryArgumentCaptor);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
    assertEquals(oldYaml, auditEntry.getOldYaml());
    assertEquals(newYaml, auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testDelete() throws JsonProcessingException {
    IPAllowlistConfig ipAllowlistConfig = getIpAllowlistConfig();
    IPAllowlistConfigDeleteEvent ipAllowlistConfigDeleteEvent =
        new IPAllowlistConfigDeleteEvent(ACCOUNT_ID, ipAllowlistConfig);
    String eventData = objectMapper.writeValueAsString(ipAllowlistConfigDeleteEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType(IP_ALLOWLIST_CONFIG_DELETED)
                                  .eventData(eventData)
                                  .resourceScope(ipAllowlistConfigDeleteEvent.getResourceScope())
                                  .resource(ipAllowlistConfigDeleteEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    String oldYaml = getYamlString(IPAllowlistDTO.builder().ipAllowlistConfig(ipAllowlistConfig).build());

    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verifyMethodInvocation(outboxEvent, auditEntryArgumentCaptor);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(auditEntry, outboxEvent);
    assertEquals(Action.DELETE, auditEntry.getAction());
    assertNull(auditEntry.getNewYaml());
    assertEquals(oldYaml, auditEntry.getOldYaml());
  }

  private IPAllowlistConfig getIpAllowlistConfig() {
    IPAllowlistConfig ipAllowlistConfig = new IPAllowlistConfig();
    ipAllowlistConfig.identifier(IDENTIFIER);
    ipAllowlistConfig.name(NAME);
    ipAllowlistConfig.description("description");
    ipAllowlistConfig.ipAddress(IP_ADDRESS);

    ipAllowlistConfig.allowedSourceType(List.of(AllowedSourceType.UI));
    ipAllowlistConfig.enabled(true);
    return ipAllowlistConfig;
  }

  private void verifyMethodInvocation(OutboxEvent outboxEvent, ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor) {
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    ipAllowlistConfigEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
  }

  private void assertAuditEntry(AuditEntry auditEntry, OutboxEvent outboxEvent) {
    assertNotNull(auditEntry);
    assertEquals(outboxEvent.getId(), auditEntry.getInsertId());
    assertEquals(ResourceTypeConstants.IP_ALLOWLIST_CONFIG, auditEntry.getResource().getType());
    assertEquals(IDENTIFIER, auditEntry.getResource().getIdentifier());
    assertEquals(ACCOUNT_ID, auditEntry.getResourceScope().getAccountIdentifier());
    assertEquals(ModuleType.CORE, auditEntry.getModule());
    assertEquals(outboxEvent.getCreatedAt().longValue(), auditEntry.getTimestamp());
  }
}

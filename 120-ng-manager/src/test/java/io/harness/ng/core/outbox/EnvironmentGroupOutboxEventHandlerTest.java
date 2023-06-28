/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.audit.ResourceTypeConstants.ENVIRONMENT_GROUP;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.AuthenticationInfoDTO;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.category.element.UnitTests;
import io.harness.cdng.envGroup.beans.EnvironmentGroupConfig;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.events.EnvironmentGroupCreateEvent;
import io.harness.cdng.events.EnvironmentGroupDeleteEvent;
import io.harness.cdng.events.EnvironmentGroupUpdateEvent;
import io.harness.context.GlobalContext;
import io.harness.ng.core.events.OutboxEventConstants;
import io.harness.outbox.OutboxEvent;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.security.SourcePrincipalContextData;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EnvironmentGroupOutboxEventHandlerTest extends CategoryTest {
  private ObjectMapper mapper = new ObjectMapper();
  private final GlobalContext globalContext = buildMockGlobalContext();
  private AutoCloseable mocks;

  private final EnvironmentGroupConfig config_created = EnvironmentGroupConfig.builder()
                                                            .identifier("eg1")
                                                            .name("eg_name")
                                                            .orgIdentifier("orgId")
                                                            .projectIdentifier("projectId")
                                                            .envIdentifiers(List.of("e_1", "e_2"))
                                                            .description("sample environment group")
                                                            .tags(Map.of("env", "test"))
                                                            .build();

  private final EnvironmentGroupConfig config_updated = EnvironmentGroupConfig.builder()
                                                            .identifier("eg1")
                                                            .name("eg_name_updated")
                                                            .orgIdentifier("orgId")
                                                            .projectIdentifier("projectId")
                                                            .envIdentifiers(List.of("e_1", "e_2", "e_3"))
                                                            .description("sample environment group after updation")
                                                            .tags(Map.of("env", "test", "nonprod", "true"))
                                                            .build();
  @Mock private AuditClientService auditClientService;

  @InjectMocks private EnvironmentGroupOutboxEventHandler handler;
  @Before
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleCreate() throws JsonProcessingException {
    ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);

    EnvironmentGroupCreateEvent event = EnvironmentGroupCreateEvent.builder()
                                            .accountIdentifier("accountId")
                                            .orgIdentifier("orgId")
                                            .environmentGroupEntity(buildEntity("accountId", config_created))
                                            .build();

    String eventData = mapper.writeValueAsString(event);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(OutboxEventConstants.ENVIRONMENT_GROUP_CREATED)
                                  .resourceScope(event.getResourceScope())
                                  .resource(event.getResource())
                                  .globalContext(globalContext)
                                  .eventData(eventData)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .build();
    handler.handle(outboxEvent);

    verify(auditClientService, times(1))
        .publishAudit(captor.capture(), any(AuthenticationInfoDTO.class), any(GlobalContext.class));

    AuditEntry entry = captor.getValue();

    assertThat(entry.getAction()).isEqualTo(Action.CREATE);
    assertThat(entry.getModule()).isEqualTo(ModuleType.CORE);
    assertThat(entry.getResource())
        .isEqualTo(ResourceDTO.builder().identifier(config_created.getIdentifier()).type(ENVIRONMENT_GROUP).build());
    assertThat(entry.getResourceScope())
        .isEqualTo(ResourceScopeDTO.builder()
                       .accountIdentifier("accountId")
                       .orgIdentifier("orgId")
                       .projectIdentifier("projectId")
                       .build());
    assertThat(entry.getNewYaml()).isEqualTo(YamlUtils.writeYamlString(config_created));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleUpdate() throws JsonProcessingException {
    ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);

    EnvironmentGroupUpdateEvent event = EnvironmentGroupUpdateEvent.builder()
                                            .accountIdentifier("accountId")
                                            .orgIdentifier("orgId")
                                            .oldEnvironmentGroupEntity(buildEntity("accountId", config_created))
                                            .newEnvironmentGroupEntity(buildEntity("accountId", config_updated))
                                            .build();

    String eventData = mapper.writeValueAsString(event);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(OutboxEventConstants.ENVIRONMENT_GROUP_UPDATED)
                                  .resourceScope(event.getResourceScope())
                                  .resource(event.getResource())
                                  .globalContext(globalContext)
                                  .eventData(eventData)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .build();
    handler.handle(outboxEvent);

    verify(auditClientService, times(1))
        .publishAudit(captor.capture(), any(AuthenticationInfoDTO.class), any(GlobalContext.class));

    AuditEntry entry = captor.getValue();

    assertThat(entry.getAction()).isEqualTo(Action.UPDATE);
    assertThat(entry.getModule()).isEqualTo(ModuleType.CORE);
    assertThat(entry.getResource())
        .isEqualTo(ResourceDTO.builder().identifier(config_updated.getIdentifier()).type(ENVIRONMENT_GROUP).build());
    assertThat(entry.getResourceScope())
        .isEqualTo(ResourceScopeDTO.builder()
                       .accountIdentifier("accountId")
                       .orgIdentifier("orgId")
                       .projectIdentifier("projectId")
                       .build());
    assertThat(entry.getNewYaml()).isEqualTo(YamlUtils.writeYamlString(config_updated));
    assertThat(entry.getOldYaml()).isEqualTo(YamlUtils.writeYamlString(config_created));
  }
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleDelete() throws JsonProcessingException {
    ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);

    EnvironmentGroupDeleteEvent event = EnvironmentGroupDeleteEvent.builder()
                                            .accountIdentifier("accountId")
                                            .orgIdentifier("orgId")
                                            .environmentGroupEntity(buildEntity("accountId", config_created))
                                            .build();

    String eventData = mapper.writeValueAsString(event);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(OutboxEventConstants.ENVIRONMENT_GROUP_DELETED)
                                  .resourceScope(event.getResourceScope())
                                  .resource(event.getResource())
                                  .globalContext(globalContext)
                                  .eventData(eventData)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .build();
    handler.handle(outboxEvent);

    verify(auditClientService, times(1))
        .publishAudit(captor.capture(), any(AuthenticationInfoDTO.class), any(GlobalContext.class));

    AuditEntry entry = captor.getValue();

    assertThat(entry.getAction()).isEqualTo(Action.DELETE);
    assertThat(entry.getModule()).isEqualTo(ModuleType.CORE);
    assertThat(entry.getResource())
        .isEqualTo(ResourceDTO.builder().identifier(config_created.getIdentifier()).type(ENVIRONMENT_GROUP).build());
    assertThat(entry.getResourceScope())
        .isEqualTo(ResourceScopeDTO.builder()
                       .accountIdentifier("accountId")
                       .orgIdentifier("orgId")
                       .projectIdentifier("projectId")
                       .build());
    assertThat(entry.getOldYaml()).isEqualTo(YamlUtils.writeYamlString(config_created));
  }

  private GlobalContext buildMockGlobalContext() {
    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);
    return globalContext;
  }

  private EnvironmentGroupEntity buildEntity(String accountId, EnvironmentGroupConfig config) {
    String yaml = YamlUtils.writeYamlString(config);
    return EnvironmentGroupEntity.builder()
        .accountId(accountId)
        .identifier(config.getIdentifier())
        .projectIdentifier(config.getProjectIdentifier())
        .orgIdentifier(config.getOrgIdentifier())
        .name(config.getName())
        .description(config.getDescription())
        .envIdentifiers(config.getEnvIdentifiers())
        .yaml(yaml)
        .build();
  }
}
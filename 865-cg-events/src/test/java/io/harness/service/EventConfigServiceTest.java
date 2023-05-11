/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.MOUNIK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CgEventConfig;
import io.harness.beans.CgEventConfig.CgEventConfigKeys;
import io.harness.beans.CgEventRule;
import io.harness.beans.PageResponse;
import io.harness.beans.WebHookEventConfig;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@Slf4j
@OwnedBy(CDC)
public class EventConfigServiceTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  public static final String GLOBAL_APP_ID = "__GLOBAL_APP_ID__";
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";

  @Mock private HPersistence hPersistence;
  @InjectMocks private EventConfigServiceImpl eventConfigService;
  @Mock Query<CgEventConfig> query;
  @Mock Query<CgEventConfig> query1;
  @Mock UpdateOperations<CgEventConfig> updateOperations;
  @Mock PageResponse<CgEventConfig> pageResponse;

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void validateEventsConfigNoEventRule() {
    CgEventConfig eventConfig = new CgEventConfig();
    eventConfig.setName("config1");
    Assertions
        .assertThatThrownBy(() -> eventConfigService.createEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, eventConfig))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Event config requires rule to be specified");
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void validateEventsConfigNoWebhookConfig() {
    CgEventRule eventRule = new CgEventRule();
    eventRule.setType(CgEventRule.CgRuleType.ALL);
    CgEventConfig cgEventConfig = CgEventConfig.builder()
                                      .name("config2")
                                      .accountId(GLOBAL_ACCOUNT_ID)
                                      .appId(GLOBAL_APP_ID)
                                      .enabled(true)
                                      .rule(eventRule)
                                      .build();
    Assertions
        .assertThatThrownBy(
            () -> eventConfigService.createEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, cgEventConfig))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Http details for configuration is required!");
  }

  private CgEventConfig getEventConfig(CgEventRule eventRule) {
    return CgEventConfig.builder()
        .name("config2")
        .accountId(GLOBAL_ACCOUNT_ID)
        .appId(GLOBAL_APP_ID)
        .enabled(true)
        .rule(eventRule)
        .config(new WebHookEventConfig())
        .build();
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void validateEventsConfigNoPipelineRule() {
    CgEventRule eventRule = new CgEventRule();
    eventRule.setType(CgEventRule.CgRuleType.ALL);
    CgEventConfig cgEventConfig = getEventConfig(eventRule);
    cgEventConfig.getConfig().setUrl("url1");
    cgEventConfig.getRule().setType(CgEventRule.CgRuleType.PIPELINE);
    Assertions
        .assertThatThrownBy(
            () -> eventConfigService.createEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, cgEventConfig))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("For Event rule type Pipeline rule need be declared");
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void validateEventsConfigNoWorkflowRule() {
    CgEventRule eventRule = new CgEventRule();
    CgEventConfig cgEventConfig = getEventConfig(eventRule);
    cgEventConfig.getConfig().setUrl("url1");
    cgEventConfig.getRule().setType(CgEventRule.CgRuleType.WORKFLOW);
    Assertions
        .assertThatThrownBy(
            () -> eventConfigService.createEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, cgEventConfig))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("For Event rule type workflow rule need be declared");
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void validateEventsConfigEventListEmpty() {
    CgEventRule eventRule = new CgEventRule();
    CgEventConfig cgEventConfig = getEventConfig(eventRule);
    cgEventConfig.getConfig().setUrl("url1");
    cgEventConfig.getRule().setType(CgEventRule.CgRuleType.PIPELINE);
    CgEventRule.PipelineRule pipelineRule = new CgEventRule.PipelineRule();
    pipelineRule.setAllPipelines(true);
    pipelineRule.setAllEvents(false);
    pipelineRule.setEvents(new LinkedList<>());
    cgEventConfig.getRule().setPipelineRule(pipelineRule);
    Assertions
        .assertThatThrownBy(
            () -> eventConfigService.createEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, cgEventConfig))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("For Event rule type Pipeline choose all events or specify at least one event");
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void validateEventsConfigEventListEmptyInWorkflowRule() {
    CgEventRule eventRule = new CgEventRule();
    CgEventConfig cgEventConfig = getEventConfig(eventRule);
    cgEventConfig.getConfig().setUrl("url1");
    cgEventConfig.getRule().setType(CgEventRule.CgRuleType.WORKFLOW);
    CgEventRule.WorkflowRule workflowRule = new CgEventRule.WorkflowRule();
    workflowRule.setAllWorkflows(true);
    workflowRule.setAllEvents(false);
    cgEventConfig.getRule().setWorkflowRule(workflowRule);
    Assertions
        .assertThatThrownBy(
            () -> eventConfigService.createEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, cgEventConfig))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("For Event rule type Workflow choose all events or specify at least one event");
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void validateEventsConfigPipelineListEmpty() {
    CgEventRule eventRule = new CgEventRule();
    CgEventConfig cgEventConfig = getEventConfig(eventRule);
    cgEventConfig.getConfig().setUrl("url1");
    cgEventConfig.getRule().setType(CgEventRule.CgRuleType.PIPELINE);
    CgEventRule.PipelineRule pipelineRule = new CgEventRule.PipelineRule();
    pipelineRule.setAllPipelines(false);
    pipelineRule.setAllEvents(true);
    pipelineRule.setPipelineIds(new LinkedList<>());
    cgEventConfig.getRule().setPipelineRule(pipelineRule);
    Assertions
        .assertThatThrownBy(
            () -> eventConfigService.createEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, cgEventConfig))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("For Event rule type Pipeline choose all pipelines or specify at least one pipeline");
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void validateEventsConfigWorkflowListEmpty() {
    CgEventRule eventRule = new CgEventRule();
    CgEventConfig cgEventConfig = getEventConfig(eventRule);
    cgEventConfig.getConfig().setUrl("url1");
    cgEventConfig.getRule().setType(CgEventRule.CgRuleType.WORKFLOW);
    CgEventRule.WorkflowRule workflowRule = new CgEventRule.WorkflowRule();
    workflowRule.setAllWorkflows(false);
    workflowRule.setAllEvents(true);
    workflowRule.setWorkflowIds(new LinkedList<>());
    cgEventConfig.getRule().setWorkflowRule(workflowRule);
    Assertions
        .assertThatThrownBy(
            () -> eventConfigService.createEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, cgEventConfig))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("For Event rule type workflow choose all workflows or specify at least one workflow");
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void validateEventsConfigNoURL() {
    CgEventRule eventRule = new CgEventRule();
    eventRule.setType(CgEventRule.CgRuleType.ALL);
    CgEventConfig cgEventConfig = getEventConfig(eventRule);
    Assertions
        .assertThatThrownBy(
            () -> eventConfigService.createEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, cgEventConfig))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("URL Required: URL field is blank!");
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void createEventsConfigRuleTypeALL() {
    CgEventConfig cgEventConfig = getEventConfigSample();
    eventConfigService.createEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, cgEventConfig);
    Mockito.verify(hPersistence).insert(eq(cgEventConfig));
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void createEventsConfigRuleTypePipeline() {
    CgEventRule eventRule = new CgEventRule();
    eventRule.setType(CgEventRule.CgRuleType.PIPELINE);
    CgEventConfig cgEventConfig = getEventConfig(eventRule);
    cgEventConfig.getConfig().setUrl("url1");
    CgEventRule.PipelineRule pipelineRule = new CgEventRule.PipelineRule();
    pipelineRule.setAllPipelines(true);
    pipelineRule.setAllEvents(true);
    cgEventConfig.getRule().setPipelineRule(pipelineRule);
    eventConfigService.createEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, cgEventConfig);
  }
  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void createEventsConfigRuleTypeWorkflow() {
    CgEventRule eventRule = new CgEventRule();
    CgEventConfig cgEventConfig = getEventConfig(eventRule);
    cgEventConfig.getConfig().setUrl("url1");
    cgEventConfig.getRule().setType(CgEventRule.CgRuleType.WORKFLOW);
    CgEventRule.WorkflowRule workflowRule = new CgEventRule.WorkflowRule();
    workflowRule.setAllWorkflows(true);
    workflowRule.setAllEvents(true);
    cgEventConfig.getRule().setWorkflowRule(workflowRule);
    eventConfigService.createEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, cgEventConfig);
  }

  private void updateSetup(CgEventConfig cgEventConfig) {
    when(hPersistence.createQuery(CgEventConfig.class)).thenReturn(query);
    when(query.filter(eq(CgEventConfigKeys.accountId), any())).thenReturn(query);
    when(query.filter(eq(CgEventConfigKeys.appId), any())).thenReturn(query);
    when(query.filter(eq(CgEventConfigKeys.uuid), eq("uuid1"))).thenReturn(query);
    when(query.filter(eq(CgEventConfigKeys.uuid), eq("uuid2"))).thenReturn(query1);
    when(query.filter(eq(CgEventConfigKeys.name), eq("config1"))).thenReturn(query);
    when(query.filter(eq(CgEventConfigKeys.name), eq("config2"))).thenReturn(query1);
    when(query.get()).thenReturn(cgEventConfig);
    when(hPersistence.delete(any(), any())).thenReturn(true);
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void updateEventsConfigNotFoundValidation() {
    CgEventConfig cgEventConfig = getEventConfigSample();
    cgEventConfig.setUuid("uuid1");
    updateSetup(cgEventConfig);
    when(query1.get()).thenReturn(null);
    CgEventConfig cgEventConfig2 = getEventConfigSample();
    cgEventConfig2.setUuid("uuid2");
    Assertions
        .assertThatThrownBy(
            () -> eventConfigService.updateEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, cgEventConfig2))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Failed to update event config: No such event config exists");
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void updateEventsConfigDuplicateValidation() {
    CgEventConfig cgEventConfig = getEventConfigSample();
    cgEventConfig.setUuid("uuid1");
    updateSetup(cgEventConfig);
    CgEventConfig cgEventConfig2 = getEventConfigSample();
    cgEventConfig2.setUuid("uuid2");
    when(query1.get()).thenReturn(cgEventConfig2);
    Assertions
        .assertThatThrownBy(
            () -> eventConfigService.updateEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, cgEventConfig2))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Duplicate Name config1");
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void updateEventsConfigSuccessValidations() {
    CgEventConfig cgEventConfig = getEventConfigSample();
    cgEventConfig.setUuid("uuid2");
    cgEventConfig.setName("config2");
    updateSetup(cgEventConfig);
    when(query1.get()).thenReturn(cgEventConfig);
    when(hPersistence.createUpdateOperations(CgEventConfig.class)).thenReturn(updateOperations);
    when(updateOperations.set(any(), any())).thenReturn(updateOperations);
    CgEventConfig returnConfig = eventConfigService.updateEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, cgEventConfig);
    Mockito.verify(hPersistence).update((CgEventConfig) any(), eq(updateOperations));
    assertThat(returnConfig.getName().equals(cgEventConfig.getName()));
    assertThat(returnConfig.getAppId().equals(cgEventConfig.getAppId()));
    assertThat(returnConfig.getAccountId().equals(cgEventConfig.getAccountId()));
    assertThat(returnConfig.getUuid().equals(cgEventConfig.getUuid()));
    assertThat(returnConfig.getConfig().getUrl().equals(cgEventConfig.getConfig().getUrl()));
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void updateEventsConfigChangeTypeSuccessfully() {
    CgEventRule eventRule = new CgEventRule();
    eventRule.setType(CgEventRule.CgRuleType.PIPELINE);
    CgEventConfig cgEventConfig = getEventConfig(eventRule);
    cgEventConfig.getConfig().setUrl("url1");
    CgEventRule.PipelineRule pipelineRule = new CgEventRule.PipelineRule();
    pipelineRule.setAllPipelines(true);
    pipelineRule.setAllEvents(true);
    cgEventConfig.getRule().setPipelineRule(pipelineRule);
    cgEventConfig.setUuid("uuid1");
    cgEventConfig.setName("config1");

    CgEventRule eventRule2 = new CgEventRule();
    CgEventConfig cgEventConfig2 = getEventConfig(eventRule2);
    cgEventConfig2.getConfig().setUrl("url1");
    cgEventConfig2.getRule().setType(CgEventRule.CgRuleType.WORKFLOW);
    CgEventRule.WorkflowRule workflowRule = new CgEventRule.WorkflowRule();
    workflowRule.setAllWorkflows(true);
    workflowRule.setAllEvents(true);
    cgEventConfig2.getRule().setWorkflowRule(workflowRule);
    cgEventConfig2.setUuid("uuid2");
    cgEventConfig2.setName("config2");

    updateSetup(cgEventConfig);
    when(query1.get()).thenReturn(cgEventConfig2);
    when(hPersistence.createUpdateOperations(CgEventConfig.class)).thenReturn(updateOperations);
    when(updateOperations.set(any(), any())).thenReturn(updateOperations);
    CgEventConfig returnConfig =
        eventConfigService.updateEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, cgEventConfig2);
    Mockito.verify(hPersistence).update((CgEventConfig) any(), eq(updateOperations));
    assertThat(returnConfig.getName().equals(cgEventConfig2.getName()));
    assertThat(returnConfig.getUuid().equals(cgEventConfig2.getUuid()));
    assertThat(returnConfig.getRule().getType().equals(CgEventRule.CgRuleType.WORKFLOW));
    assertThat(returnConfig.getRule().getWorkflowRule().equals(workflowRule));
  }

  private CgEventConfig getEventConfigSample() {
    CgEventRule eventRule = new CgEventRule();
    eventRule.setType(CgEventRule.CgRuleType.ALL);
    CgEventConfig cgEventConfig = CgEventConfig.builder()
                                      .name("config1")
                                      .accountId(GLOBAL_ACCOUNT_ID)
                                      .appId(GLOBAL_APP_ID)
                                      .enabled(true)
                                      .rule(eventRule)
                                      .build();
    WebHookEventConfig config = new WebHookEventConfig();
    cgEventConfig.setConfig(config);
    cgEventConfig.getConfig().setUrl("url1");
    return cgEventConfig;
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void deleteEventsConfigAllValidations() {
    CgEventConfig cgEventConfig = getEventConfigSample();
    cgEventConfig.setUuid("uuid1");
    when(hPersistence.createQuery(CgEventConfig.class)).thenReturn(query);
    when(query.filter(eq(CgEventConfigKeys.accountId), any())).thenReturn(query);
    when(query.filter(eq(CgEventConfigKeys.appId), any())).thenReturn(query);
    when(query.filter(eq(CgEventConfigKeys.uuid), eq("uuid1"))).thenReturn(query);
    when(query.filter(eq(CgEventConfigKeys.uuid), eq("uuid2"))).thenReturn(query1);
    when(hPersistence.delete(any(), any())).thenReturn(true);
    when(query.get()).thenReturn(cgEventConfig);
    when(query1.get()).thenReturn(null);
    Assertions
        .assertThatThrownBy(() -> eventConfigService.deleteEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, "uuid2"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Event Config does not exist");
    eventConfigService.deleteEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, "uuid1");
    Mockito.verify(hPersistence).delete(any(), eq("uuid1"));
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void getEventsConfigAllValidations() {
    CgEventConfig cgEventConfig = getEventConfigSample();
    cgEventConfig.setUuid("uuid1");
    when(hPersistence.createQuery(CgEventConfig.class)).thenReturn(query);
    when(query.filter(eq(CgEventConfigKeys.accountId), any())).thenReturn(query);
    when(query.filter(eq(CgEventConfigKeys.appId), any())).thenReturn(query);
    when(query.filter(eq(CgEventConfigKeys.uuid), eq("uuid1"))).thenReturn(query);
    when(query.filter(eq(CgEventConfigKeys.uuid), eq("uuid2"))).thenReturn(query1);
    when(query.get()).thenReturn(cgEventConfig);
    when(query1.get()).thenReturn(null);
    assertThat(eventConfigService.getEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, "uuid2")).isNull();
    CgEventConfig retEventConfig = eventConfigService.getEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, "uuid1");
    assertThat(retEventConfig).isNotNull();
    assertThat(retEventConfig.getName().equals("config1")).isTrue();
    assertThat(retEventConfig.getUuid().equals("uuid1")).isTrue();
    assertThat(retEventConfig.getAccountId().equals(GLOBAL_ACCOUNT_ID)).isTrue();
    assertThat(retEventConfig.getAppId().equals(GLOBAL_APP_ID)).isTrue();
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void getEventsConfigByNameAllValidations() {
    CgEventConfig cgEventConfig = getEventConfigSample();
    cgEventConfig.setName("eventc1");
    when(hPersistence.createQuery(CgEventConfig.class)).thenReturn(query);
    when(query.filter(eq(CgEventConfigKeys.accountId), any())).thenReturn(query);
    when(query.filter(eq(CgEventConfigKeys.appId), any())).thenReturn(query);
    when(query.filter(eq(CgEventConfigKeys.name), eq("eventc1"))).thenReturn(query);
    when(query.filter(eq(CgEventConfigKeys.name), eq("eventc2"))).thenReturn(query1);
    when(query.get()).thenReturn(cgEventConfig);
    when(query1.get()).thenReturn(null);
    assertThat(eventConfigService.getEventsConfigByName(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, "eventc2")).isNull();
    CgEventConfig retEventConfig =
        eventConfigService.getEventsConfigByName(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, "eventc1");
    assertThat(retEventConfig).isNotNull();
    assertThat(retEventConfig.getName().equals("eventc1")).isTrue();
    assertThat(retEventConfig.getAccountId().equals(GLOBAL_ACCOUNT_ID)).isTrue();
    assertThat(retEventConfig.getAppId().equals(GLOBAL_APP_ID)).isTrue();
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void getAllEventsConfigAllValidations() {
    CgEventConfig cgEventConfig = getEventConfigSample();
    when(hPersistence.query(eq(CgEventConfig.class), any())).thenReturn(pageResponse);
    when(pageResponse.getResponse()).thenReturn(Arrays.asList(cgEventConfig));
    List<CgEventConfig> cgEventConfigs = eventConfigService.listAllEventsConfig(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID);
    assertThat(cgEventConfigs).isNotNull();
    assertThat(cgEventConfigs.size()).isEqualTo(1);
    assertThat(cgEventConfigs.get(0).getAppId().equals(GLOBAL_APP_ID)).isTrue();
    assertThat(cgEventConfigs.get(0).getAccountId().equals(GLOBAL_ACCOUNT_ID)).isTrue();
    assertThat(cgEventConfigs.get(0).getName().equals("config1")).isTrue();
  }
}

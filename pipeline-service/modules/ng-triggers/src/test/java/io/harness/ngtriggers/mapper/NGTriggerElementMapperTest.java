/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.mapper;

import static io.harness.ngtriggers.beans.source.NGTriggerType.SCHEDULED;
import static io.harness.ngtriggers.beans.source.NGTriggerType.WEBHOOK;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.CLOSED;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.OPENED;
import static io.harness.ngtriggers.beans.source.webhook.WebhookEvent.PULL_REQUEST;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.ROHITKARELIA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.config.NGTriggerConfig;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory.TriggerEventHistoryKeys;
import io.harness.ngtriggers.beans.source.scheduled.CronTriggerSpec;
import io.harness.ngtriggers.beans.source.scheduled.ScheduledTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.CustomWebhookTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerSpec;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.rule.Owner;
import io.harness.utils.YamlPipelineUtils;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PIPELINE)
public class NGTriggerElementMapperTest extends CategoryTest {
  private String ngCustomTriggerYaml;
  private String ngTriggerGitConnYaml;
  private String ngTriggerCronYaml;
  @Mock private TriggerEventHistoryRepository triggerEventHistoryRepository;
  @InjectMocks private NGTriggerElementMapper ngTriggerElementMapper;
  Sort sort = Sort.by(TriggerEventHistoryKeys.createdAt).descending();

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = getClass().getClassLoader();

    String gitYaml = "ng-trigger-v0.yaml";
    ngTriggerGitConnYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(gitYaml)), StandardCharsets.UTF_8);

    String fileNameForCustomPayloadTrigger = "ng-custom-trigger-v0.yaml";
    ngCustomTriggerYaml = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(fileNameForCustomPayloadTrigger)), StandardCharsets.UTF_8);

    String fileNgTriggerCronYaml = "ng-trigger-cron-v0.yaml";
    ngTriggerCronYaml = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(fileNgTriggerCronYaml)), StandardCharsets.UTF_8);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testToTriggerConfig() throws Exception {
    NGTriggerConfig trigger = YamlPipelineUtils.read(ngTriggerGitConnYaml, NGTriggerConfig.class);

    assertThat(trigger).isNotNull();
    assertThat(trigger.getIdentifier()).isEqualTo("first_trigger");
    assertThat(trigger.getSource().getType()).isEqualTo(WEBHOOK);
    assertThat(trigger.getSource().getSpec()).isInstanceOfAny(WebhookTriggerConfig.class);

    WebhookTriggerConfig webhookTriggerConfig = (WebhookTriggerConfig) trigger.getSource().getSpec();
    assertThat(webhookTriggerConfig.getType()).isEqualTo("GITHUB");
    assertThat(webhookTriggerConfig.getSpec()).isNotNull();

    WebhookTriggerSpec webhookTriggerConfigSpec = webhookTriggerConfig.getSpec();
    assertThat(webhookTriggerConfigSpec.getEvent()).isEqualTo(PULL_REQUEST);
    assertThat(webhookTriggerConfigSpec.getActions()).containsExactlyInAnyOrder(OPENED, CLOSED);
    assertThat(webhookTriggerConfigSpec.getPathFilters()).containsExactlyInAnyOrder("path1", "path2");
    assertThat(webhookTriggerConfigSpec.getJexlCondition()).isEqualTo("true");
    assertThat(webhookTriggerConfigSpec.getPayloadConditions()).isNotNull();
    assertThat(webhookTriggerConfigSpec.getPayloadConditions().size()).isEqualTo(3);

    Set<String> payloadConditionSet = webhookTriggerConfigSpec.getPayloadConditions()
                                          .stream()
                                          .map(webhookPayloadCondition
                                              -> new StringBuilder(128)
                                                     .append(webhookPayloadCondition.getKey())
                                                     .append(':')
                                                     .append(webhookPayloadCondition.getOperator())
                                                     .append(':')
                                                     .append(webhookPayloadCondition.getValue())
                                                     .toString())
                                          .collect(Collectors.toSet());

    assertThat(payloadConditionSet)
        .containsOnly("sourceBranch:equals:dev", "targetBranch:in:master, on-prem",
            "${pull_request.number}:regex:^pr-[0-9a-f]{7}$");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testToTriggerConfigForCustomPayloadTrigger() throws Exception {
    NGTriggerConfig trigger = YamlPipelineUtils.read(ngCustomTriggerYaml, NGTriggerConfig.class);
    assertCustomTrigger(trigger);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testToTriggerConfigForCronTrigger() throws Exception {
    NGTriggerConfig trigger = YamlPipelineUtils.read(ngTriggerCronYaml, NGTriggerConfig.class);

    assertThat(trigger).isNotNull();
    assertThat(trigger.getIdentifier()).isEqualTo("cronTrigger");
    assertThat(trigger.getSource().getType()).isEqualTo(SCHEDULED);
    assertThat(trigger.getSource().getSpec()).isInstanceOfAny(ScheduledTriggerConfig.class);

    ScheduledTriggerConfig scheduledTriggerConfig = (ScheduledTriggerConfig) trigger.getSource().getSpec();
    assertThat(scheduledTriggerConfig.getType()).isEqualTo("Cron");
    assertThat(scheduledTriggerConfig.getSpec()).isNotNull();

    CronTriggerSpec cronTriggerSpec = (CronTriggerSpec) scheduledTriggerConfig.getSpec();
    assertThat(cronTriggerSpec.getExpression()).isEqualTo("20 4 * * *");
  }

  private void assertCustomTrigger(NGTriggerConfig trigger) {
    assertThat(trigger).isNotNull();
    assertThat(trigger.getIdentifier()).isEqualTo("customPayload");
    assertThat(trigger.getSource().getType()).isEqualTo(WEBHOOK);
    assertThat(trigger.getSource().getSpec()).isInstanceOfAny(WebhookTriggerConfig.class);

    WebhookTriggerConfig webhookTriggerConfig = (WebhookTriggerConfig) trigger.getSource().getSpec();
    assertThat(webhookTriggerConfig.getType()).isEqualTo("CUSTOM");
    assertThat(webhookTriggerConfig.getSpec()).isNotNull();

    CustomWebhookTriggerSpec customWebhookTriggerSpec = (CustomWebhookTriggerSpec) webhookTriggerConfig.getSpec();
    assertThat(customWebhookTriggerSpec.getPayloadConditions()).isNotNull();
    assertThat(customWebhookTriggerSpec.getPayloadConditions().size()).isEqualTo(1);

    Set<String> payloadConditionSet = customWebhookTriggerSpec.getPayloadConditions()
                                          .stream()
                                          .map(webhookPayloadCondition
                                              -> new StringBuilder(128)
                                                     .append(webhookPayloadCondition.getKey())
                                                     .append(':')
                                                     .append(webhookPayloadCondition.getOperator())
                                                     .append(':')
                                                     .append(webhookPayloadCondition.getValue())
                                                     .toString())
                                          .collect(Collectors.toSet());

    assertThat(payloadConditionSet).containsOnly("<+trigger.payload.project.team>:in:cd, ci");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testFetchLatestExecutionForTrigger() throws Exception {
    NGTriggerEntity ngTriggerEntity1 = NGTriggerEntity.builder()
                                           .accountId("account")
                                           .orgIdentifier("org")
                                           .projectIdentifier("project")
                                           .targetIdentifier("pipeline1")
                                           .identifier("id1")
                                           .build();
    NGTriggerEntity ngTriggerEntity2 = NGTriggerEntity.builder()
                                           .accountId("account")
                                           .orgIdentifier("org")
                                           .projectIdentifier("project")
                                           .targetIdentifier("pipeline2")
                                           .identifier("id1")
                                           .build();
    NGTriggerEntity ngTriggerEntity3 = NGTriggerEntity.builder()
                                           .accountId("account")
                                           .orgIdentifier("org")
                                           .projectIdentifier("project")
                                           .targetIdentifier("pipeline3")
                                           .identifier("id1")
                                           .build();
    List<TriggerEventHistory> pipeLine1Triggers = new ArrayList<>();
    List<TriggerEventHistory> pipeLine2Triggers = new ArrayList<>();
    List<TriggerEventHistory> allTriggersInProject = new ArrayList<>();
    TriggerEventHistory pipeline1Trigger =
        TriggerEventHistory.builder().triggerIdentifier("id1").targetIdentifier("pipeline1").build();
    TriggerEventHistory pipeline2Trigger =
        TriggerEventHistory.builder().triggerIdentifier("id1").targetIdentifier("pipeline2").build();
    pipeLine1Triggers.add(pipeline1Trigger);
    pipeLine2Triggers.add(pipeline2Trigger);
    allTriggersInProject.add(pipeline1Trigger);
    allTriggersInProject.add(pipeline2Trigger);
    Criteria criteria1 = Criteria.where(TriggerEventHistoryKeys.accountId)
                             .is(ngTriggerEntity1.getAccountId())
                             .and(TriggerEventHistoryKeys.orgIdentifier)
                             .is(ngTriggerEntity1.getOrgIdentifier())
                             .and(TriggerEventHistoryKeys.projectIdentifier)
                             .is(ngTriggerEntity1.getProjectIdentifier())
                             .and(TriggerEventHistoryKeys.targetIdentifier)
                             .is(ngTriggerEntity1.getTargetIdentifier())
                             .and(TriggerEventHistoryKeys.triggerIdentifier)
                             .is(ngTriggerEntity1.getIdentifier())
                             .and(TriggerEventHistoryKeys.executionNotAttempted)
                             .ne(true);
    when(triggerEventHistoryRepository.findAllWithSort(criteria1, sort)).thenReturn(pipeLine1Triggers);

    Criteria criteria2 = Criteria.where(TriggerEventHistoryKeys.accountId)
                             .is(ngTriggerEntity2.getAccountId())
                             .and(TriggerEventHistoryKeys.orgIdentifier)
                             .is(ngTriggerEntity2.getOrgIdentifier())
                             .and(TriggerEventHistoryKeys.projectIdentifier)
                             .is(ngTriggerEntity2.getProjectIdentifier())
                             .and(TriggerEventHistoryKeys.targetIdentifier)
                             .is(ngTriggerEntity2.getTargetIdentifier())
                             .and(TriggerEventHistoryKeys.triggerIdentifier)
                             .is(ngTriggerEntity2.getIdentifier())
                             .and(TriggerEventHistoryKeys.executionNotAttempted)
                             .ne(true);
    when(triggerEventHistoryRepository.findAllWithSort(criteria2, sort)).thenReturn(pipeLine2Triggers);

    Criteria criteria3 = Criteria.where(TriggerEventHistoryKeys.accountId)
                             .is(ngTriggerEntity3.getAccountId())
                             .and(TriggerEventHistoryKeys.orgIdentifier)
                             .is(ngTriggerEntity3.getOrgIdentifier())
                             .and(TriggerEventHistoryKeys.projectIdentifier)
                             .is(ngTriggerEntity3.getProjectIdentifier())
                             .and(TriggerEventHistoryKeys.targetIdentifier)
                             .is(ngTriggerEntity3.getTargetIdentifier())
                             .and(TriggerEventHistoryKeys.triggerIdentifier)
                             .is(ngTriggerEntity3.getIdentifier())
                             .and(TriggerEventHistoryKeys.executionNotAttempted)
                             .ne(true);
    when(triggerEventHistoryRepository.findAllWithSort(criteria3, sort)).thenReturn(allTriggersInProject);
    assertThat(ngTriggerElementMapper.fetchLatestExecutionForTrigger(ngTriggerEntity1).get().getTargetIdentifier())
        .isEqualTo(ngTriggerEntity1.getTargetIdentifier());
    assertThat(ngTriggerElementMapper.fetchLatestExecutionForTrigger(ngTriggerEntity2).get().getTargetIdentifier())
        .isEqualTo(ngTriggerEntity2.getTargetIdentifier());
    assertThat(ngTriggerElementMapper.fetchLatestExecutionForTrigger(ngTriggerEntity3).get().getTargetIdentifier())
        .isEqualTo(ngTriggerEntity1.getTargetIdentifier());
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testUpdateEntityYmlWithEnabledValue() throws IOException {
    String fileNameForCustomPayloadTrigger1 = "ng-custom-trigger-v0.yaml";
    ClassLoader classLoader = getClass().getClassLoader();

    String ngCustomTriggerYaml1 = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(fileNameForCustomPayloadTrigger1)), StandardCharsets.UTF_8);
    NGTriggerEntity ngTriggerEntity1 = NGTriggerEntity.builder()
                                           .accountId("account")
                                           .orgIdentifier("org")
                                           .projectIdentifier("project")
                                           .targetIdentifier("pipeline1")
                                           .identifier("id1")
                                           .yaml(ngCustomTriggerYaml1)
                                           .enabled(false)
                                           .build();
    ngTriggerElementMapper.updateEntityYmlWithEnabledValue(ngTriggerEntity1);
    assertThat(ngTriggerEntity1.getYaml()).startsWith("trigger");
    assertThat(ngTriggerEntity1.getYaml()).doesNotStartWith("---");

    ngTriggerEntity1.setYaml("yaml");
    ngTriggerElementMapper.updateEntityYmlWithEnabledValue(ngTriggerEntity1);
    assertThat(ngTriggerEntity1.getYaml()).isEqualTo("yaml");
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testToNGTriggerDetailsResponseDTOPlanExecutionId() throws IOException {
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .accountId("account")
                                          .orgIdentifier("org")
                                          .projectIdentifier("project")
                                          .targetIdentifier("pipeline3")
                                          .identifier("id1")
                                          .build();
    TriggerEventHistory triggerEventHistory = TriggerEventHistory.builder()
                                                  .accountId("account")
                                                  .orgIdentifier("org")
                                                  .projectIdentifier("project")
                                                  .triggerIdentifier("identifier")
                                                  .planExecutionId("planExecutionId")
                                                  .build();
    when(triggerEventHistoryRepository.findAllWithSort(any(), any()))
        .thenReturn(Collections.singletonList(triggerEventHistory));
    assertThat(ngTriggerElementMapper.toNGTriggerDetailsResponseDTO(ngTriggerEntity, true, true, false, false)
                   .getLastTriggerExecutionDetails()
                   .getPlanExecutionId())
        .isEqualTo("planExecutionId");
    triggerEventHistory.setPlanExecutionId(null);
    when(triggerEventHistoryRepository.findAllWithSort(any(), any()))
        .thenReturn(Collections.singletonList(triggerEventHistory));

    // planExecutionId is null
    assertThat(ngTriggerElementMapper.toNGTriggerDetailsResponseDTO(ngTriggerEntity, true, true, false, false)
                   .getLastTriggerExecutionDetails()
                   .getPlanExecutionId())
        .isNull();
  }
}
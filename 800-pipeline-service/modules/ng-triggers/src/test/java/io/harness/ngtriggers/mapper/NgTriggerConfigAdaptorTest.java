/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.mapper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.source.NGTriggerType.SCHEDULED;
import static io.harness.ngtriggers.beans.source.NGTriggerType.WEBHOOK;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.AWS_CODECOMMIT;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.BITBUCKET;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.CUSTOM;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.GITHUB;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.GITLAB;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.CONTAINS;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.ENDS_WITH;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.EQUALS;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.IN;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.NOT_EQUALS;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.NOT_IN;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.STARTS_WITH;
import static io.harness.rule.OwnerRule.ADWAIT;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.config.NGTriggerConfig;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.source.NGTriggerSource;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerSpecV2;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.scheduled.CronTriggerSpec;
import io.harness.ngtriggers.beans.source.scheduled.ScheduledTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerSpecV2;
import io.harness.ngtriggers.beans.source.webhook.v2.awscodecommit.event.AwsCodeCommitTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.action.BitbucketPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.event.BitbucketTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.custom.CustomTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAction;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubIssueCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.action.GitlabPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.event.GitlabTriggerEvent;
import io.harness.ngtriggers.beans.target.NGTriggerTarget;
import io.harness.ngtriggers.beans.target.TargetType;
import io.harness.ngtriggers.beans.target.pipeline.PipelineTargetSpec;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.rule.Owner;
import io.harness.webhook.WebhookConfigProvider;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class NgTriggerConfigAdaptorTest extends CategoryTest {
  private static final String ACC_ID = "acc";
  private static final String ORG_ID = "org";
  private static final String PROJ_ID = "proj";
  private static final String PIPELINE_ID = "pipeline";
  private static final String TRIGGER_ID = "first_trigger";
  private static final String NAME = "first trigger";
  private static final String INPUT_YAML = "pipeline:\n"
      + "  identifier: secrethttp1\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: qaStage\n"
      + "        spec:\n"
      + "          infrastructure:\n"
      + "            infrastructureDefinition:\n"
      + "              spec:\n"
      + "                releaseName: releaseName1";
  private static final String CON_REF = "conn";
  private static final String REPO_NAME = "myrepo";
  public static final String EXPRESSION = "* * * *";
  private NGTriggerEntity ngTriggerEntity;
  private List<TriggerEventDataCondition> payloadConditions;
  private List<TriggerEventDataCondition> headerConditions;
  private static final String JEXL = "true";

  @Mock private TriggerEventHistoryRepository triggerEventHistoryRepository;
  @Mock private WebhookConfigProvider webhookConfigProvider;
  @InjectMocks @Inject private NGTriggerElementMapper ngTriggerElementMapper;
  private ClassLoader classLoader;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    ngTriggerEntity = NGTriggerEntity.builder()
                          .accountId(ACC_ID)
                          .orgIdentifier(ORG_ID)
                          .projectIdentifier(PROJ_ID)
                          .targetType(TargetType.PIPELINE)
                          .targetIdentifier(PIPELINE_ID)
                          .identifier(TRIGGER_ID)
                          .build();

    payloadConditions = asList(TriggerEventDataCondition.builder().key("k1").operator(EQUALS).value("v1").build(),
        TriggerEventDataCondition.builder().key("k2").operator(NOT_EQUALS).value("v2").build(),
        TriggerEventDataCondition.builder().key("k3").operator(IN).value("v3,c").build(),
        TriggerEventDataCondition.builder().key("k4").operator(NOT_IN).value("v4").build(),
        TriggerEventDataCondition.builder().key("k5").operator(STARTS_WITH).value("v5").build(),
        TriggerEventDataCondition.builder().key("k6").operator(ENDS_WITH).value("v6").build(),
        TriggerEventDataCondition.builder().key("k7").operator(CONTAINS).value("v7").build());

    headerConditions = asList(TriggerEventDataCondition.builder().key("h1").operator(EQUALS).value("v1").build());

    classLoader = getClass().getClassLoader();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testConvertScheduledTriggerFromV0ToV2() {
    ngTriggerEntity.setType(SCHEDULED);
    ScheduledTriggerConfig triggerConfig = ScheduledTriggerConfig.builder()
                                               .type("Cron")
                                               .spec(CronTriggerSpec.builder().expression(EXPRESSION).build())
                                               .build();
    NGTriggerConfig ngTriggerConfig =
        NGTriggerConfig.builder()
            .enabled(true)
            .name(NAME)
            .identifier(TRIGGER_ID)
            .target(NGTriggerTarget.builder()
                        .targetIdentifier(PIPELINE_ID)
                        .spec(PipelineTargetSpec.builder().runtimeInputYaml(INPUT_YAML).build())
                        .build())
            .source(NGTriggerSource.builder().type(SCHEDULED).spec(triggerConfig).build())
            .build();

    NGTriggerConfigV2 ngTriggerConfigV2 = NgTriggerConfigAdaptor.convertFromV0ToV2(ngTriggerConfig, ngTriggerEntity);
    assertRootLevelProperties(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertThat(ngTriggerSourceV2).isNotNull();
    assertThat(ngTriggerSourceV2.getType()).isEqualTo(SCHEDULED);
    NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerSourceV2.getSpec();
    assertThat(ScheduledTriggerConfig.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    ScheduledTriggerConfig scheduledTriggerConfig = (ScheduledTriggerConfig) ngTriggerSpecV2;
    assertThat(scheduledTriggerConfig).isEqualTo(triggerConfig);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testConvertGithubPRTriggerFromV0ToV2() throws Exception {
    initiateTest(
        "ng-trigger-github-pr-v0.yaml", GITHUB, GithubTriggerEvent.PULL_REQUEST, asList(GithubPRAction.values()));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testConvertGithubIssueCommentTriggerFromV0ToV2() throws Exception {
    initiateTest("ng-trigger-github-issue-comment-v0.yaml", GITHUB, GithubTriggerEvent.ISSUE_COMMENT,
        asList(GithubIssueCommentAction.values()));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testConvertGithubPushTriggerFromV0ToV2() throws Exception {
    initiateTest("ng-trigger-github-push-v0.yaml", GITHUB, GithubTriggerEvent.PUSH, emptyList());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testConvertGitlabPRTriggerFromV0ToV2() throws Exception {
    initiateTest(
        "ng-trigger-gitlab-pr-v0.yaml", GITLAB, GitlabTriggerEvent.MERGE_REQUEST, asList(GitlabPRAction.values()));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testConvertGitlabPushTriggerFromV0ToV2() throws Exception {
    initiateTest("ng-trigger-gitlab-push-v0.yaml", GITLAB, GitlabTriggerEvent.PUSH, emptyList());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testConvertBitbucketPRTriggerFromV0ToV2() throws Exception {
    initiateTest("ng-trigger-bitbucket-pr-v0.yaml", BITBUCKET, BitbucketTriggerEvent.PULL_REQUEST,
        asList(BitbucketPRAction.values()));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testConvertBitbucketPushTriggerFromV0ToV2() throws Exception {
    initiateTest("ng-trigger-bitbucket-push-v0.yaml", BITBUCKET, BitbucketTriggerEvent.PUSH, emptyList());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testConvertAwsCodeCommitPushTriggerFromV0ToV2() throws Exception {
    initiateTest("ng-trigger-awscodecommit-push-v0.yaml", AWS_CODECOMMIT, AwsCodeCommitTriggerEvent.PUSH, emptyList());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testConvertCustomTriggerFromV0ToV2() throws Exception {
    String v0Yaml = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-custom-v0.yaml")), StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(NGTriggerEntity.builder()
                                                                                       .ymlVersion(0l)
                                                                                       .yaml(v0Yaml)
                                                                                       .accountId(ACC_ID)
                                                                                       .orgIdentifier(ORG_ID)
                                                                                       .projectIdentifier(PROJ_ID)
                                                                                       .build());

    assertRootLevelProperties(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertThat(ngTriggerSourceV2).isNotNull();
    assertThat(ngTriggerSourceV2.getType()).isEqualTo(WEBHOOK);
    NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerSourceV2.getSpec();
    assertThat(WebhookTriggerConfigV2.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) ngTriggerSpecV2;
    assertThat(webhookTriggerConfigV2.getType()).isEqualTo(CUSTOM);
    assertThat(CustomTriggerSpec.class.isAssignableFrom(webhookTriggerConfigV2.getSpec().getClass())).isTrue();
    assertPayloadProperties(CUSTOM, webhookTriggerConfigV2.getSpec());
  }

  private void initiateTest(String fileName, WebhookTriggerType webhookTriggerType, GitEvent gitEvent,
      List<? extends GitAction> gitActions) throws Exception {
    // Trigger mapping with older yaml.. (Build NgTriggerConfigV2 using older yaml flow)
    String v0Yaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(fileName)), StandardCharsets.UTF_8);

    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(NGTriggerEntity.builder()
                                                                                       .ymlVersion(0l)
                                                                                       .yaml(v0Yaml)
                                                                                       .accountId(ACC_ID)
                                                                                       .orgIdentifier(ORG_ID)
                                                                                       .projectIdentifier(PROJ_ID)
                                                                                       .build());

    assertRootLevelProperties(ngTriggerConfigV2);
    assertCommonWebhookProperties(ngTriggerConfigV2, webhookTriggerType, gitEvent, gitActions);

    // Make sure, yaml generation for above NgConfigV2 is valid. (convert it back into Config object and assert)
    String configV2Yaml = ngTriggerElementMapper.generateNgTriggerConfigV2Yaml(ngTriggerConfigV2);
    ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(NGTriggerEntity.builder().yaml(configV2Yaml).build());
    assertRootLevelProperties(ngTriggerConfigV2);
    assertCommonWebhookProperties(ngTriggerConfigV2, webhookTriggerType, gitEvent, gitActions);
  }

  private void assertRootLevelProperties(NGTriggerConfigV2 ngTriggerConfigV2) {
    assertThat(ngTriggerConfigV2).isNotNull();
    assertThat(ngTriggerConfigV2.getIdentifier()).isEqualTo(TRIGGER_ID);
    assertThat(ngTriggerConfigV2.getEnabled()).isTrue();
    assertThat(ngTriggerConfigV2.getInputYaml().trim()).isEqualTo(INPUT_YAML);
    assertThat(ngTriggerConfigV2.getPipelineIdentifier()).isEqualTo(PIPELINE_ID);
    assertThat(ngTriggerConfigV2.getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(ngTriggerConfigV2.getProjectIdentifier()).isEqualTo(PROJ_ID);
    assertThat(ngTriggerConfigV2.getName()).isEqualTo(NAME);
  }

  private void assertCommonWebhookProperties(NGTriggerConfigV2 ngTriggerConfigV2, WebhookTriggerType webhookTriggerType,
      GitEvent gitEvent, List<? extends GitAction> gitActions) {
    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertThat(ngTriggerSourceV2).isNotNull();
    assertThat(ngTriggerSourceV2.getType()).isEqualTo(WEBHOOK);
    NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerSourceV2.getSpec();
    assertThat(WebhookTriggerConfigV2.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) ngTriggerSpecV2;
    assertThat(webhookTriggerConfigV2.getType()).isEqualTo(webhookTriggerType);

    WebhookTriggerSpecV2 webhookTriggerConfigV2Spec = webhookTriggerConfigV2.getSpec();
    assertPayloadProperties(webhookTriggerType, webhookTriggerConfigV2Spec);
    assertGitProperties(gitEvent, gitActions, webhookTriggerConfigV2Spec);
  }

  private void assertGitProperties(
      GitEvent gitEvent, List<? extends GitAction> gitActions, WebhookTriggerSpecV2 webhookTriggerConfigV2Spec) {
    assertThat(webhookTriggerConfigV2Spec.fetchGitAware().fetchRepoName()).isEqualTo(REPO_NAME);
    assertThat(webhookTriggerConfigV2Spec.fetchGitAware().fetchConnectorRef()).isEqualTo(CON_REF);
    assertThat(webhookTriggerConfigV2Spec.fetchGitAware().fetchAutoAbortPreviousExecutions()).isFalse();
    assertThat(webhookTriggerConfigV2Spec.fetchGitAware().fetchEvent()).isEqualTo(gitEvent);
    assertThat(webhookTriggerConfigV2Spec.fetchGitAware().fetchActions()).containsAll(gitActions);
  }

  private void assertPayloadProperties(
      WebhookTriggerType webhookTriggerType, WebhookTriggerSpecV2 webhookTriggerConfigV2Spec) {
    assertThat(webhookTriggerConfigV2Spec.fetchPayloadAware().fetchPayloadConditions()).isEqualTo(payloadConditions);
    if (webhookTriggerType != AWS_CODECOMMIT) {
      assertThat(webhookTriggerConfigV2Spec.fetchPayloadAware().fetchHeaderConditions()).isEqualTo(headerConditions);
    } else {
      assertThat(webhookTriggerConfigV2Spec.fetchPayloadAware().fetchHeaderConditions()).isEmpty();
    }
    assertThat(webhookTriggerConfigV2Spec.fetchPayloadAware().fetchJexlCondition()).isEqualTo(JEXL);
  }
}

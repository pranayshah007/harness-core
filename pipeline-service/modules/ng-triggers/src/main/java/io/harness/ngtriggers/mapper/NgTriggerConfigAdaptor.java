/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.mapper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER_SRE;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.TriggerException;
import io.harness.ngtriggers.beans.config.NGTriggerConfig;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2.NGTriggerConfigV2Builder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2.NGTriggerSourceV2Builder;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.scheduled.ScheduledTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.AwsCodeCommitTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.BitbucketTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.CustomWebhookTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.GithubTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.GitlabTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.WebhookAction;
import io.harness.ngtriggers.beans.source.webhook.WebhookCondition;
import io.harness.ngtriggers.beans.source.webhook.WebhookEvent;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2.WebhookTriggerConfigV2Builder;
import io.harness.ngtriggers.beans.source.webhook.v2.awscodecommit.AwsCodeCommitSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.awscodecommit.AwsCodeCommitSpec.AwsCodeCommitSpecBuilder;
import io.harness.ngtriggers.beans.source.webhook.v2.awscodecommit.event.AwsCodeCommitPushSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.awscodecommit.event.AwsCodeCommitTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.BitbucketSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.BitbucketSpec.BitbucketSpecBuilder;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.action.BitbucketPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.event.BitbucketPRSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.event.BitbucketPushSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.event.BitbucketTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.custom.CustomTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.custom.CustomTriggerSpec.CustomTriggerSpecBuilder;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.GithubSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.GithubSpec.GithubSpecBuilder;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubIssueCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubReleaseAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubIssueCommentSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubPRSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubPushSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubReleaseSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.GitlabSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.GitlabSpec.GitlabSpecBuilder;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.action.GitlabPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.event.GitlabPRSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.event.GitlabPushSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.event.GitlabTriggerEvent;
import io.harness.ngtriggers.beans.target.pipeline.PipelineTargetSpec;
import io.harness.ngtriggers.conditionchecker.ConditionOperator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PIPELINE)
public class NgTriggerConfigAdaptor {
  public NGTriggerConfigV2 convertFromV0ToV2(NGTriggerConfig ngTriggerConfig, NGTriggerEntity ngTriggerEntity) {
    NGTriggerConfigV2Builder triggerConfigV2Builder =
        NGTriggerConfigV2.builder()
            .name(ngTriggerConfig.getName())
            .description(ngTriggerConfig.getDescription())
            .identifier(ngTriggerConfig.getIdentifier())
            .orgIdentifier(ngTriggerEntity.getOrgIdentifier())
            .projectIdentifier(ngTriggerEntity.getProjectIdentifier())
            .pipelineIdentifier(ngTriggerConfig.getTarget().getTargetIdentifier())
            .enabled(ngTriggerConfig.getEnabled())
            .inputYaml(((PipelineTargetSpec) ngTriggerConfig.getTarget().getSpec()).getRuntimeInputYaml());

    if (ngTriggerConfig.getSource().getType() == NGTriggerType.WEBHOOK) {
      handleWebhookConfig(ngTriggerConfig, triggerConfigV2Builder);
    } else if (ngTriggerConfig.getSource().getType() == NGTriggerType.SCHEDULED) {
      ScheduledTriggerConfig scheduledTriggerConfig = (ScheduledTriggerConfig) ngTriggerConfig.getSource().getSpec();
      triggerConfigV2Builder.source(
          NGTriggerSourceV2.builder().type(NGTriggerType.SCHEDULED).spec(scheduledTriggerConfig).build());
    }

    return triggerConfigV2Builder.build();
  }

  private static void handleWebhookConfig(
      NGTriggerConfig ngTriggerConfig, NGTriggerConfigV2Builder triggerConfigV2Builder) {
    WebhookTriggerConfigV2Builder webhookTriggerConfigV2Builder = WebhookTriggerConfigV2.builder();

    NGTriggerSourceV2Builder ngTriggerSourceV2Builder = NGTriggerSourceV2.builder().type(NGTriggerType.WEBHOOK);

    WebhookTriggerConfig webhookTriggerConfig = (WebhookTriggerConfig) ngTriggerConfig.getSource().getSpec();
    WebhookTriggerSpec webhookTriggerSpec = webhookTriggerConfig.getSpec();

    if (GithubTriggerSpec.class.isAssignableFrom(webhookTriggerSpec.getClass())) {
      handleGithubConfig(webhookTriggerConfigV2Builder, (GithubTriggerSpec) webhookTriggerSpec);
    } else if (GitlabTriggerSpec.class.isAssignableFrom(webhookTriggerSpec.getClass())) {
      handleGitlabConfig(webhookTriggerConfigV2Builder, (GitlabTriggerSpec) webhookTriggerSpec);
    } else if (BitbucketTriggerSpec.class.isAssignableFrom(webhookTriggerSpec.getClass())) {
      handleBitbucketConfig(webhookTriggerConfigV2Builder, (BitbucketTriggerSpec) webhookTriggerSpec);
    } else if (AwsCodeCommitTriggerSpec.class.isAssignableFrom(webhookTriggerSpec.getClass())) {
      handleAwsCodeCommitTriggerConfig(webhookTriggerConfigV2Builder, (AwsCodeCommitTriggerSpec) webhookTriggerSpec);
    } else if (CustomWebhookTriggerSpec.class.isAssignableFrom(webhookTriggerSpec.getClass())) {
      handleCustomConfig(webhookTriggerConfigV2Builder, (CustomWebhookTriggerSpec) webhookTriggerSpec);
    } else {
      throw new TriggerException(
          "Invalid webhook trigger type encountered with Version 0 while converting to Version 2" + webhookTriggerSpec,
          USER_SRE);
    }

    triggerConfigV2Builder.source(ngTriggerSourceV2Builder.spec(webhookTriggerConfigV2Builder.build()).build());
  }

  private static void handleCustomConfig(
      WebhookTriggerConfigV2Builder webhookTriggerConfigV2Builder, CustomWebhookTriggerSpec customWebhookTriggerSpec) {
    webhookTriggerConfigV2Builder.type(WebhookTriggerType.CUSTOM);
    CustomTriggerSpecBuilder customTriggerSpecBuilder = CustomTriggerSpec.builder();
    initCustomSpec(customWebhookTriggerSpec, customTriggerSpecBuilder);
    webhookTriggerConfigV2Builder.spec(customTriggerSpecBuilder.build());
  }

  private static void handleGithubConfig(
      WebhookTriggerConfigV2Builder webhookTriggerConfigV2Builder, GithubTriggerSpec webhookTriggerSpec) {
    webhookTriggerConfigV2Builder.type(WebhookTriggerType.GITHUB);
    GithubSpecBuilder githubSpecBuilder = GithubSpec.builder();
    initGithubSpec(webhookTriggerSpec, githubSpecBuilder);
    webhookTriggerConfigV2Builder.spec(githubSpecBuilder.build());
  }

  private static void handleGitlabConfig(
      WebhookTriggerConfigV2Builder webhookTriggerConfigV2Builder, GitlabTriggerSpec webhookTriggerSpec) {
    webhookTriggerConfigV2Builder.type(WebhookTriggerType.GITLAB);
    GitlabSpecBuilder gitlabSpecBuilder = GitlabSpec.builder();
    initGitlabSpec(webhookTriggerSpec, gitlabSpecBuilder);
    webhookTriggerConfigV2Builder.spec(gitlabSpecBuilder.build());
  }

  private static void handleBitbucketConfig(
      WebhookTriggerConfigV2Builder webhookTriggerConfigV2Builder, BitbucketTriggerSpec bitbucketTriggerSpec) {
    webhookTriggerConfigV2Builder.type(WebhookTriggerType.BITBUCKET);
    BitbucketSpecBuilder bitbucketSpecBuilder = BitbucketSpec.builder();
    initBitbucketSpecSpec(bitbucketTriggerSpec, bitbucketSpecBuilder);
    webhookTriggerConfigV2Builder.spec(bitbucketSpecBuilder.build());
  }

  private static void handleAwsCodeCommitTriggerConfig(
      WebhookTriggerConfigV2Builder webhookTriggerConfigV2Builder, AwsCodeCommitTriggerSpec awsCodeCommitTriggerSpec) {
    webhookTriggerConfigV2Builder.type(WebhookTriggerType.AWS_CODECOMMIT);
    AwsCodeCommitSpecBuilder awsCodeCommitSpecBuilder = AwsCodeCommitSpec.builder();
    initAwsCodeCommitSpec(awsCodeCommitTriggerSpec, awsCodeCommitSpecBuilder);
    webhookTriggerConfigV2Builder.spec(awsCodeCommitSpecBuilder.build());
  }

  private static void initGithubSpec(GithubTriggerSpec githubTriggerSpec, GithubSpecBuilder githubSpecBuilder) {
    if (githubTriggerSpec.getEvent() == WebhookEvent.PULL_REQUEST) {
      githubSpecBuilder.type(GithubTriggerEvent.PULL_REQUEST);
      githubSpecBuilder.spec(
          GithubPRSpec.builder()
              .connectorRef(githubTriggerSpec.getGitRepoSpec().getIdentifier())
              .repoName(githubTriggerSpec.getGitRepoSpec().getRepoName())
              .headerConditions(mapToTriggerEventDataCondition(githubTriggerSpec.getHeaderConditions()))
              .payloadConditions(mapToTriggerEventDataCondition(githubTriggerSpec.getPayloadConditions()))
              .jexlCondition(githubTriggerSpec.getJexlCondition())
              .autoAbortPreviousExecutions(false)
              .actions(getGithubPrActions(githubTriggerSpec))
              .build());
    } else if (githubTriggerSpec.getEvent() == WebhookEvent.PUSH) {
      githubSpecBuilder.type(GithubTriggerEvent.PUSH);
      githubSpecBuilder.spec(
          GithubPushSpec.builder()
              .connectorRef(githubTriggerSpec.getGitRepoSpec().getIdentifier())
              .repoName(githubTriggerSpec.getGitRepoSpec().getRepoName())
              .headerConditions(mapToTriggerEventDataCondition(githubTriggerSpec.getHeaderConditions()))
              .payloadConditions(mapToTriggerEventDataCondition(githubTriggerSpec.getPayloadConditions()))
              .jexlCondition(githubTriggerSpec.getJexlCondition())
              .autoAbortPreviousExecutions(false)
              .build());

    } else if (githubTriggerSpec.getEvent() == WebhookEvent.ISSUE_COMMENT) {
      githubSpecBuilder.type(GithubTriggerEvent.ISSUE_COMMENT);
      githubSpecBuilder.spec(
          GithubIssueCommentSpec.builder()
              .connectorRef(githubTriggerSpec.getGitRepoSpec().getIdentifier())
              .repoName(githubTriggerSpec.getGitRepoSpec().getRepoName())
              .headerConditions(mapToTriggerEventDataCondition(githubTriggerSpec.getHeaderConditions()))
              .payloadConditions(mapToTriggerEventDataCondition(githubTriggerSpec.getPayloadConditions()))
              .jexlCondition(githubTriggerSpec.getJexlCondition())
              .autoAbortPreviousExecutions(false)
              .actions(getGithubIssueCommentActions(githubTriggerSpec))
              .build());
    } else if (githubTriggerSpec.getEvent() == WebhookEvent.RELEASE) {
      githubSpecBuilder.type(GithubTriggerEvent.RELEASE);
      githubSpecBuilder.spec(
          GithubReleaseSpec.builder()
              .connectorRef(githubTriggerSpec.getGitRepoSpec().getIdentifier())
              .repoName(githubTriggerSpec.getGitRepoSpec().getRepoName())
              .headerConditions(mapToTriggerEventDataCondition(githubTriggerSpec.getHeaderConditions()))
              .payloadConditions(mapToTriggerEventDataCondition(githubTriggerSpec.getPayloadConditions()))
              .jexlCondition(githubTriggerSpec.getJexlCondition())
              .autoAbortPreviousExecutions(false)
              .actions(getGithubReleaseActions(githubTriggerSpec))
              .build());
    } else {
      throw new TriggerException(
          "Invalid Event Type encountered in Trigger with Version 0 while converting to Version 2"
              + githubTriggerSpec.getEvent(),
          USER_SRE);
    }
  }

  private static List<TriggerEventDataCondition> mapToTriggerEventDataCondition(List<WebhookCondition> conditions) {
    if (isEmpty(conditions)) {
      return emptyList();
    }

    List<TriggerEventDataCondition> triggerEventDataConditions = new ArrayList<>();

    conditions.forEach(condition -> {
      ConditionOperator conditionOperator =
          WebhookConditionMapperEnum.getCondtionOperationMappingForString(condition.getOperator());
      if (conditionOperator != null) {
        triggerEventDataConditions.add(TriggerEventDataCondition.builder()
                                           .key(condition.getKey())
                                           .operator(conditionOperator)
                                           .value(condition.getValue())
                                           .build());
      }
    });

    return triggerEventDataConditions;
  }

  private static void initGitlabSpec(GitlabTriggerSpec gitlabTriggerSpec, GitlabSpecBuilder gitlabSpecBuilder) {
    if (gitlabTriggerSpec.getEvent() == WebhookEvent.MERGE_REQUEST) {
      gitlabSpecBuilder.type(GitlabTriggerEvent.MERGE_REQUEST);
      gitlabSpecBuilder.spec(
          GitlabPRSpec.builder()
              .connectorRef(gitlabTriggerSpec.getGitRepoSpec().getIdentifier())
              .repoName(gitlabTriggerSpec.getGitRepoSpec().getRepoName())
              .headerConditions(mapToTriggerEventDataCondition(gitlabTriggerSpec.getHeaderConditions()))
              .payloadConditions(mapToTriggerEventDataCondition(gitlabTriggerSpec.getPayloadConditions()))
              .jexlCondition(gitlabTriggerSpec.getJexlCondition())
              .autoAbortPreviousExecutions(false)
              .actions(getGitlabPrActions(gitlabTriggerSpec))
              .build());
    } else if (gitlabTriggerSpec.getEvent() == WebhookEvent.PUSH) {
      gitlabSpecBuilder.type(GitlabTriggerEvent.PUSH);
      gitlabSpecBuilder.spec(
          GitlabPushSpec.builder()
              .connectorRef(gitlabTriggerSpec.getGitRepoSpec().getIdentifier())
              .repoName(gitlabTriggerSpec.getGitRepoSpec().getRepoName())
              .headerConditions(mapToTriggerEventDataCondition(gitlabTriggerSpec.getHeaderConditions()))
              .payloadConditions(mapToTriggerEventDataCondition(gitlabTriggerSpec.getPayloadConditions()))
              .jexlCondition(gitlabTriggerSpec.getJexlCondition())
              .autoAbortPreviousExecutions(false)
              .build());
    } else {
      throw new TriggerException(
          "Invalid Event Type encountered in Trigger with Version 0 while converting to Version 2"
              + gitlabTriggerSpec.getEvent(),
          USER_SRE);
    }
  }

  private static void initBitbucketSpecSpec(
      BitbucketTriggerSpec bitbucketTriggerSpec, BitbucketSpecBuilder bitbucketSpecBuilder) {
    if (bitbucketTriggerSpec.getEvent() == WebhookEvent.PULL_REQUEST) {
      bitbucketSpecBuilder.type(BitbucketTriggerEvent.PULL_REQUEST);
      bitbucketSpecBuilder.spec(
          BitbucketPRSpec.builder()
              .connectorRef(bitbucketTriggerSpec.getGitRepoSpec().getIdentifier())
              .repoName(bitbucketTriggerSpec.getGitRepoSpec().getRepoName())
              .headerConditions(mapToTriggerEventDataCondition(bitbucketTriggerSpec.getHeaderConditions()))
              .payloadConditions(mapToTriggerEventDataCondition(bitbucketTriggerSpec.getPayloadConditions()))
              .jexlCondition(bitbucketTriggerSpec.getJexlCondition())
              .autoAbortPreviousExecutions(false)
              .actions(getBitbucketPrActions(bitbucketTriggerSpec))
              .build());
    } else if (bitbucketTriggerSpec.getEvent() == WebhookEvent.PUSH) {
      bitbucketSpecBuilder.type(BitbucketTriggerEvent.PUSH);
      bitbucketSpecBuilder.spec(
          BitbucketPushSpec.builder()
              .connectorRef(bitbucketTriggerSpec.getGitRepoSpec().getIdentifier())
              .repoName(bitbucketTriggerSpec.getGitRepoSpec().getRepoName())
              .headerConditions(mapToTriggerEventDataCondition(bitbucketTriggerSpec.getHeaderConditions()))
              .payloadConditions(mapToTriggerEventDataCondition(bitbucketTriggerSpec.getPayloadConditions()))
              .jexlCondition(bitbucketTriggerSpec.getJexlCondition())
              .autoAbortPreviousExecutions(false)
              .build());
    } else {
      throw new TriggerException(
          "Invalid Event Type encountered in Trigger with Version 0 while converting to Version 2"
              + bitbucketTriggerSpec.getEvent(),
          USER_SRE);
    }
  }

  private static void initAwsCodeCommitSpec(
      AwsCodeCommitTriggerSpec awsCodeCommitTriggerSpec, AwsCodeCommitSpecBuilder awsCodeCommitSpecBuilder) {
    if (awsCodeCommitTriggerSpec.getEvent() == WebhookEvent.PUSH) {
      awsCodeCommitSpecBuilder.type(AwsCodeCommitTriggerEvent.PUSH);
      awsCodeCommitSpecBuilder.spec(
          AwsCodeCommitPushSpec.builder()
              .connectorRef(awsCodeCommitTriggerSpec.getGitRepoSpec().getIdentifier())
              .repoName(awsCodeCommitTriggerSpec.getGitRepoSpec().getRepoName())
              .payloadConditions(mapToTriggerEventDataCondition(awsCodeCommitTriggerSpec.getPayloadConditions()))
              .jexlCondition(awsCodeCommitTriggerSpec.getJexlCondition())
              .build());
    } else {
      throw new TriggerException(
          "Invalid Event Type encountered in Trigger with Version 0 while converting to Version 2"
              + awsCodeCommitTriggerSpec.getEvent(),
          USER_SRE);
    }
  }

  private static void initCustomSpec(
      CustomWebhookTriggerSpec customWebhookTriggerSpec, CustomTriggerSpecBuilder customTriggerSpecBuilder) {
    customTriggerSpecBuilder
        .headerConditions(mapToTriggerEventDataCondition(customWebhookTriggerSpec.getHeaderConditions()))
        .payloadConditions(mapToTriggerEventDataCondition(customWebhookTriggerSpec.getPayloadConditions()))
        .jexlCondition(customWebhookTriggerSpec.getJexlCondition());
  }

  private static List<GithubPRAction> getGithubPrActions(GithubTriggerSpec githubTriggerSpec) {
    if (isEmpty(githubTriggerSpec.getActions())) {
      return emptyList();
    }

    List<GitAction> gitActions = getGitActions(githubTriggerSpec.getActions(), Arrays.asList(GithubPRAction.values()));
    return gitActions.stream().map(gitAction -> (GithubPRAction) gitAction).collect(toList());
  }

  private static List<GithubIssueCommentAction> getGithubIssueCommentActions(GithubTriggerSpec githubTriggerSpec) {
    if (isEmpty(githubTriggerSpec.getActions())) {
      return emptyList();
    }

    List<GitAction> gitActions =
        getGitActions(githubTriggerSpec.getActions(), Arrays.asList(GithubIssueCommentAction.values()));
    return gitActions.stream().map(gitAction -> (GithubIssueCommentAction) gitAction).collect(toList());
  }

  private static List<GithubReleaseAction> getGithubReleaseActions(GithubTriggerSpec githubTriggerSpec) {
    if (isEmpty(githubTriggerSpec.getActions())) {
      return emptyList();
    }

    List<GitAction> gitActions =
        getGitActions(githubTriggerSpec.getActions(), Arrays.asList(GithubReleaseAction.values()));
    return gitActions.stream().map(gitAction -> (GithubReleaseAction) gitAction).collect(toList());
  }

  private static List<GitlabPRAction> getGitlabPrActions(GitlabTriggerSpec gitlabTriggerSpec) {
    if (isEmpty(gitlabTriggerSpec.getActions())) {
      return emptyList();
    }

    List<GitAction> gitActions = getGitActions(gitlabTriggerSpec.getActions(), Arrays.asList(GitlabPRAction.values()));
    return gitActions.stream().map(gitAction -> (GitlabPRAction) gitAction).collect(toList());
  }

  private static List<BitbucketPRAction> getBitbucketPrActions(BitbucketTriggerSpec bitbucketTriggerSpec) {
    if (isEmpty(bitbucketTriggerSpec.getActions())) {
      return emptyList();
    }

    List<GitAction> gitActions =
        getGitActions(bitbucketTriggerSpec.getActions(), Arrays.asList(BitbucketPRAction.values()));
    return gitActions.stream().map(gitAction -> (BitbucketPRAction) gitAction).collect(toList());
  }

  private static List<GitAction> getGitActions(
      List<WebhookAction> webhookActions, List<? extends GitAction> gitActions) {
    if (isEmpty(webhookActions)) {
      return emptyList();
    }

    Set<String> parsedValues =
        webhookActions.stream().map(webhookAction -> webhookAction.getParsedValue()).collect(toSet());

    // Need special handlnig due to issue in current impl
    handleGithubDataBugIfNeeded(parsedValues, gitActions);

    return gitActions.stream()
        .filter(githubPRAction -> parsedValues.contains(githubPRAction.getParsedValue()))
        .collect(toList());
  }

  /**
   * In WebhookAction enum, Following types has dame jsonProperties, so while de-serialisation,
   * it can get mapped to any action.
   */
  private static void handleGithubDataBugIfNeeded(Set<String> parsedValues, List<? extends GitAction> gitActions) {
    if (GithubPRAction.class.isAssignableFrom(gitActions.get(0).getClass())
        || GithubIssueCommentAction.class.isAssignableFrom(gitActions.get(0).getClass())) {
      if (parsedValues.contains("created")) {
        parsedValues.remove("created");
        parsedValues.add("create");
      }

      if (parsedValues.contains("deleted")) {
        parsedValues.remove("deleted");
        parsedValues.add("delete");
      }

      if (parsedValues.contains("update")) {
        parsedValues.remove("update");
        parsedValues.add("edit");
      }
    }
    if (GithubPRAction.class.isAssignableFrom(gitActions.get(0).getClass())) {
      if (parsedValues.contains("edit")) {
        parsedValues.remove("edit");
        parsedValues.add("update");
      }
    }
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.trigger;

import io.harness.data.structure.EmptyPredicate;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.github.GithubSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubIssueCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubReleaseAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubEventSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubIssueCommentSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubPRSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubPushSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubReleaseSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubTriggerEvent;

import software.wings.beans.trigger.GithubAction;
import software.wings.beans.trigger.WebHookTriggerCondition;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class GithubWebhookHandlerImpl implements WebhookHandler {
  @Override
  public WebhookTriggerConfigV2 getConfig(WebHookTriggerCondition condition, Map<CgEntityId, NGYamlFile> yamlFileMap) {
    GithubEventSpec githubEventSpec;
    GithubTriggerEvent event;

    String connectorRef = "__PLEASE_FIX_ME__";
    if (StringUtils.isNotBlank(condition.getGitConnectorId())) {
      NGYamlFile yamlFile = yamlFileMap.get(
          CgEntityId.builder().type(NGMigrationEntityType.CONNECTOR).id(condition.getGitConnectorId()).build());
      if (yamlFile != null) {
        connectorRef = MigratorUtility.getIdentifierWithScope(yamlFile.getNgEntityDetail());
      }
    }

    switch (condition.getEventTypes().get(0)) {
      case PULL_REQUEST:
        List<GithubPRAction> prActions = new ArrayList<>();
        if (EmptyPredicate.isNotEmpty(condition.getActions())) {
          prActions = condition.getActions().stream().map(this::getPRAction).collect(Collectors.toList());
        }
        event = GithubTriggerEvent.PULL_REQUEST;
        githubEventSpec = GithubPRSpec.builder()
                              .repoName(condition.getRepoName())
                              .actions(prActions)
                              .connectorRef(connectorRef)
                              .autoAbortPreviousExecutions(true)
                              .build();
        break;
      case RELEASE:
        event = GithubTriggerEvent.RELEASE;
        List<GithubReleaseAction> releaseActions = new ArrayList<>();
        if (EmptyPredicate.isNotEmpty(condition.getActions())) {
          releaseActions = condition.getActions().stream().map(this::getReleaseAction).collect(Collectors.toList());
        }
        githubEventSpec = GithubReleaseSpec.builder()
                              .repoName(condition.getRepoName())
                              .actions(releaseActions)
                              .connectorRef(connectorRef)
                              .autoAbortPreviousExecutions(true)
                              .build();
        break;
      case ISSUE:
        event = GithubTriggerEvent.ISSUE_COMMENT;
        List<GithubIssueCommentAction> issueCommentActions = new ArrayList<>();
        if (EmptyPredicate.isNotEmpty(condition.getActions())) {
          issueCommentActions = condition.getActions().stream().map(this::getIssueAction).collect(Collectors.toList());
        }
        githubEventSpec = GithubIssueCommentSpec.builder()
                              .repoName(condition.getRepoName())
                              .actions(issueCommentActions)
                              .connectorRef(connectorRef)
                              .autoAbortPreviousExecutions(true)
                              .build();
        break;
      case PUSH:
      default:
        event = GithubTriggerEvent.PUSH;
        githubEventSpec = GithubPushSpec.builder()
                              .repoName(condition.getRepoName())
                              .connectorRef(connectorRef)
                              .autoAbortPreviousExecutions(true)
                              .build();
        break;
    }

    return WebhookTriggerConfigV2.builder()
        .spec(GithubSpec.builder().type(event).spec(githubEventSpec).build())
        .type(WebhookTriggerType.GITHUB)
        .build();
  }

  private GithubPRAction getPRAction(GithubAction action) {
    return Arrays.stream(GithubPRAction.values())
        .filter(a -> a.name().equals(action.name()))
        .findFirst()
        .orElse(GithubPRAction.OPEN);
  }

  private GithubReleaseAction getReleaseAction(GithubAction action) {
    return Arrays.stream(GithubReleaseAction.values())
        .filter(a -> a.name().equals(action.name()))
        .findFirst()
        .orElse(GithubReleaseAction.CREATE);
  }

  private GithubIssueCommentAction getIssueAction(GithubAction action) {
    return Arrays.stream(GithubIssueCommentAction.values())
        .filter(a -> a.name().equals(action.name()))
        .findFirst()
        .orElse(GithubIssueCommentAction.CREATE);
  }
}

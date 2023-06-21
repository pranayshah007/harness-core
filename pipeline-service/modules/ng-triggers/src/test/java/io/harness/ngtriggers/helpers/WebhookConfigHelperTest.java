/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.BT_PULL_REQUEST_CREATED;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.BT_PULL_REQUEST_DECLINED;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.BT_PULL_REQUEST_MERGED;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.BT_PULL_REQUEST_UPDATED;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.CLOSED;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.CREATED;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.DELETED;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.EDITED;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.GITLAB_CLOSE;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.GITLAB_MERGED;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.GITLAB_OPEN;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.GITLAB_REOPEN;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.GITLAB_SYNC;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.GITLAB_UPDATED;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.LABELED;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.OPENED;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.REOPENED;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.SYNCHRONIZED;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.UNLABELED;
import static io.harness.ngtriggers.beans.source.webhook.WebhookEvent.BRANCH;
import static io.harness.ngtriggers.beans.source.webhook.WebhookEvent.DELETE;
import static io.harness.ngtriggers.beans.source.webhook.WebhookEvent.MERGE_REQUEST;
import static io.harness.ngtriggers.beans.source.webhook.WebhookEvent.PULL_REQUEST;
import static io.harness.ngtriggers.beans.source.webhook.WebhookEvent.PUSH;
import static io.harness.ngtriggers.beans.source.webhook.WebhookEvent.REPOSITORY;
import static io.harness.ngtriggers.beans.source.webhook.WebhookEvent.TAG;
import static io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo.AWS_CODECOMMIT;
import static io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo.BITBUCKET;
import static io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo.GITHUB;
import static io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo.GITLAB;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.JAMIE;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.WebhookAction;
import io.harness.ngtriggers.beans.source.webhook.WebhookEvent;
import io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerSpecV2;
import io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.action.AzureRepoIssueCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.action.AzureRepoPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.action.BitbucketPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.action.BitbucketPRCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.event.BitbucketTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAware;
import io.harness.ngtriggers.beans.source.webhook.v2.git.PayloadAware;
import io.harness.ngtriggers.beans.source.webhook.v2.github.GithubSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubIssueCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubReleaseAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubEventSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubPushSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.action.GitlabMRCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.action.GitlabPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.event.GitlabTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.harness.action.HarnessIssueCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.harness.action.HarnessPRAction;
import io.harness.rule.Owner;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class WebhookConfigHelperTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetSourceRepoToEvent() {
    Map<WebhookSourceRepo, List<WebhookEvent>> map = WebhookConfigHelper.getSourceRepoToEvent();
    Set<WebhookEvent> events = new HashSet<>(map.get(GITHUB));
    events.addAll(map.get(WebhookSourceRepo.GITLAB));
    events.addAll(map.get(WebhookSourceRepo.BITBUCKET));
    events.addAll(map.get(WebhookSourceRepo.AWS_CODECOMMIT));

    Set<WebhookEvent> allEvents = EnumSet.allOf(WebhookEvent.class);
    Set<WebhookEvent> eventsNotPresent = new HashSet<>();
    for (WebhookEvent event : allEvents) {
      if (!events.contains(event)) {
        eventsNotPresent.add(event);
      }
    }

    // Need to ad support for these
    assertThat(eventsNotPresent).containsOnly(DELETE, REPOSITORY, BRANCH, TAG);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetGithubTriggerEvents() {
    List<GithubTriggerEvent> events = WebhookConfigHelper.getGithubTriggerEvents();
    assertThat(events).containsOnly(
        GithubTriggerEvent.PULL_REQUEST, GithubTriggerEvent.ISSUE_COMMENT, GithubTriggerEvent.PUSH);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetGitlabTriggerEvents() {
    List<GitlabTriggerEvent> events = WebhookConfigHelper.getGitlabTriggerEvents();
    assertThat(events).containsOnly(
        GitlabTriggerEvent.MERGE_REQUEST, GitlabTriggerEvent.MR_COMMENT, GitlabTriggerEvent.PUSH);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetBitbucketTriggerEvents() {
    List<BitbucketTriggerEvent> events = WebhookConfigHelper.getBitbucketTriggerEvents();
    assertThat(events).containsOnly(
        BitbucketTriggerEvent.PULL_REQUEST, BitbucketTriggerEvent.PR_COMMENT, BitbucketTriggerEvent.PUSH);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testRetrieveGitAware() {
    GithubEventSpec gitAware = GithubPushSpec.builder().build();
    WebhookTriggerSpecV2 triggerSpec = GithubSpec.builder().type(GithubTriggerEvent.PUSH).spec(gitAware).build();
    WebhookTriggerConfigV2 webhookTriggerConfig =
        WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).spec(triggerSpec).build();
    GitAware retrievedGitAware = WebhookConfigHelper.retrieveGitAware(webhookTriggerConfig);
    assertThat(retrievedGitAware).isSameAs(gitAware);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testRetrievePayloadAware() {
    GithubEventSpec payloadAware = GithubPushSpec.builder().build();
    WebhookTriggerSpecV2 triggerSpec = GithubSpec.builder().type(GithubTriggerEvent.PUSH).spec(payloadAware).build();
    WebhookTriggerConfigV2 webhookTriggerConfig =
        WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).spec(triggerSpec).build();
    PayloadAware retrievedPayloadAware = WebhookConfigHelper.retrievePayloadAware(webhookTriggerConfig);
    assertThat(retrievedPayloadAware).isSameAs(payloadAware);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testRetrieveHeaderConditions() {
    TriggerEventDataCondition headerCondition = TriggerEventDataCondition.builder().build();
    GithubEventSpec payloadAware = GithubPushSpec.builder().headerConditions(List.of(headerCondition)).build();
    WebhookTriggerSpecV2 triggerSpec = GithubSpec.builder().type(GithubTriggerEvent.PUSH).spec(payloadAware).build();
    WebhookTriggerConfigV2 webhookTriggerConfig =
        WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).spec(triggerSpec).build();
    List<TriggerEventDataCondition> headerConditions =
        WebhookConfigHelper.retrieveHeaderConditions(webhookTriggerConfig);
    assertThat(headerConditions).containsOnly(headerCondition);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testIsGitSpec() {
    WebhookTriggerConfigV2 config = WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build();
    assertThat(WebhookConfigHelper.isGitSpec(config)).isTrue();
    config = WebhookTriggerConfigV2.builder().type(WebhookTriggerType.BITBUCKET).build();
    assertThat(WebhookConfigHelper.isGitSpec(config)).isTrue();
    config = WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITLAB).build();
    assertThat(WebhookConfigHelper.isGitSpec(config)).isTrue();
    config = WebhookTriggerConfigV2.builder().type(WebhookTriggerType.AWS_CODECOMMIT).build();
    assertThat(WebhookConfigHelper.isGitSpec(config)).isTrue();
    config = WebhookTriggerConfigV2.builder().type(WebhookTriggerType.AZURE).build();
    assertThat(WebhookConfigHelper.isGitSpec(config)).isTrue();
    config = WebhookTriggerConfigV2.builder().type(WebhookTriggerType.HARNESS).build();
    assertThat(WebhookConfigHelper.isGitSpec(config)).isTrue();
    config = WebhookTriggerConfigV2.builder().type(WebhookTriggerType.CUSTOM).build();
    assertThat(WebhookConfigHelper.isGitSpec(config)).isFalse();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetAzureRepoPRAction() {
    assertThat(WebhookConfigHelper.getAzureRepoPRAction()
                   .stream()
                   .map(AzureRepoPRAction::getValue)
                   .collect(Collectors.toList()))
        .containsOnly("Create", "Update", "Merge");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetGithubPRAction() {
    assertThat(
        WebhookConfigHelper.getGithubPRAction().stream().map(GithubPRAction::getValue).collect(Collectors.toList()))
        .containsOnly("Close", "Edit", "Open", "Reopen", "Label", "Unlabel", "Synchronize");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetHarnessPRAction() {
    assertThat(
        WebhookConfigHelper.getHarnessPRAction().stream().map(HarnessPRAction::getValue).collect(Collectors.toList()))
        .containsOnly("Close", "Edit", "Open", "Reopen", "Label", "Unlabel", "Synchronize");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetGitlabPRAction() {
    assertThat(
        WebhookConfigHelper.getGitlabPRAction().stream().map(GitlabPRAction::getValue).collect(Collectors.toList()))
        .containsOnly("Close", "Update", "Open", "Reopen", "Merge", "Sync");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetBitbucketPRAction() {
    assertThat(WebhookConfigHelper.getBitbucketPRAction()
                   .stream()
                   .map(BitbucketPRAction::getValue)
                   .collect(Collectors.toList()))
        .containsOnly("Create", "Update", "Merge", "Decline");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetBitbucketPRCommentAction() {
    assertThat(WebhookConfigHelper.getBitbucketPRCommentAction()
                   .stream()
                   .map(BitbucketPRCommentAction::getValue)
                   .collect(Collectors.toList()))
        .containsOnly("Create", "Edit", "Delete");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetGithubIssueCommentAction() {
    assertThat(WebhookConfigHelper.getGithubIssueCommentAction()
                   .stream()
                   .map(GithubIssueCommentAction::getValue)
                   .collect(Collectors.toList()))
        .containsOnly("Create", "Edit", "Delete");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetHarnessIssueCommentAction() {
    assertThat(WebhookConfigHelper.getHarnessIssueCommentAction()
                   .stream()
                   .map(HarnessIssueCommentAction::getValue)
                   .collect(Collectors.toList()))
        .containsOnly("Create", "Edit", "Delete");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetAzureRepoIssueCommentAction() {
    assertThat(WebhookConfigHelper.getAzureRepoIssueCommentAction()
                   .stream()
                   .map(AzureRepoIssueCommentAction::getValue)
                   .collect(Collectors.toList()))
        .containsOnly("Create", "Edit", "Delete");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetGitlabMRCommentAction() {
    assertThat(WebhookConfigHelper.getGitlabMRCommentAction()
                   .stream()
                   .map(GitlabMRCommentAction::getValue)
                   .collect(Collectors.toList()))
        .containsOnly("Create");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetGithubReleaseAction() {
    assertThat(WebhookConfigHelper.getGithubReleaseAction()
                   .stream()
                   .map(GithubReleaseAction::getValue)
                   .collect(Collectors.toList()))
        .containsOnly("Create", "Edit", "Delete", "Prerelease", "Publish", "Release", "Unpublish");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetWebhookTriggerType() {
    assertThat(WebhookConfigHelper.getWebhookTriggerType()
                   .stream()
                   .map(WebhookTriggerType::getValue)
                   .collect(Collectors.toList()))
        .containsOnly("AzureRepo", "Github", "Gitlab", "Bitbucket", "Harness", "AwsCodeCommit", "Custom");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetActionList() {
    List<WebhookAction> actionsList = WebhookConfigHelper.getActionsList(null, PUSH);
    assertThat(actionsList).isEmpty();

    actionsList = WebhookConfigHelper.getActionsList(GITHUB, PUSH);
    assertThat(actionsList).isEmpty();

    actionsList = WebhookConfigHelper.getActionsList(GITHUB, PULL_REQUEST);
    assertThat(actionsList)
        .containsExactlyInAnyOrder(CLOSED, EDITED, LABELED, OPENED, REOPENED, SYNCHRONIZED, UNLABELED);

    actionsList.clear();
    assertThatThrownBy(() -> WebhookConfigHelper.getActionsList(GITHUB, MERGE_REQUEST))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Event MERGE_REQUEST not a github event");

    actionsList.clear();
    actionsList = WebhookConfigHelper.getActionsList(BITBUCKET, PUSH);
    assertThat(actionsList).isEmpty();
    actionsList = WebhookConfigHelper.getActionsList(BITBUCKET, PULL_REQUEST);
    assertThat(actionsList)
        .containsExactlyInAnyOrder(
            BT_PULL_REQUEST_CREATED, BT_PULL_REQUEST_UPDATED, BT_PULL_REQUEST_MERGED, BT_PULL_REQUEST_DECLINED);

    actionsList.clear();
    actionsList = WebhookConfigHelper.getActionsList(GITLAB, MERGE_REQUEST);
    assertThat(actionsList)
        .containsExactlyInAnyOrder(
            GITLAB_OPEN, GITLAB_CLOSE, GITLAB_REOPEN, GITLAB_MERGED, GITLAB_UPDATED, GITLAB_SYNC);

    actionsList.clear();
    assertThatThrownBy(() -> WebhookConfigHelper.getActionsList(GITLAB, PULL_REQUEST))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Event PULL_REQUEST not a gitlab event");

    actionsList = WebhookConfigHelper.getActionsList(GITLAB, PUSH);
    assertThat(actionsList).isEmpty();
    actionsList = WebhookConfigHelper.getActionsList(GITLAB, DELETE);
    assertThat(actionsList).isEmpty();
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testGetActionListAWS() {
    List<WebhookAction> actionsList = WebhookConfigHelper.getActionsList(AWS_CODECOMMIT, BRANCH);
    assertThat(actionsList).containsExactlyInAnyOrder(CREATED, DELETED);
    actionsList = WebhookConfigHelper.getActionsList(AWS_CODECOMMIT, TAG);
    assertThat(actionsList).containsExactlyInAnyOrder(CREATED, DELETED);
    actionsList = WebhookConfigHelper.getActionsList(AWS_CODECOMMIT, PUSH);
    assertThat(actionsList).isEmpty();
    assertThatThrownBy(() -> WebhookConfigHelper.getActionsList(AWS_CODECOMMIT, PULL_REQUEST))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Event PULL_REQUEST not an AWS code commit event");
  }
}

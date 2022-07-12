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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.source.webhook.WebhookAction;
import io.harness.ngtriggers.beans.source.webhook.WebhookEvent;
import io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo;
import io.harness.rule.Owner;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

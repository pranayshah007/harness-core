/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.webhook;

import static io.harness.annotations.dev.HarnessTeam.CI;

import static java.util.Collections.emptySet;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@OwnedBy(CI)
public enum WebhookAction {
  // Github
  @JsonProperty("created") CREATED("create", "created"),
  @JsonProperty("closed") CLOSED("close", "closed"),
  @JsonProperty("edited") EDITED("edit", "edited"),
  @JsonProperty("updated") UPDATED("update", "updated"),
  @JsonProperty("opened") OPENED("open", "opened"),
  @JsonProperty("reopened") REOPENED("reopen", "reopened"),
  @JsonProperty("labeled") LABELED("label", "labeled"),
  @JsonProperty("unlabeled") UNLABELED("unlabel", "unlabeled"),
  @JsonProperty("deleted") DELETED("delete", "deleted"),
  @JsonProperty("synchronized") SYNCHRONIZED("sync", "synchronized"),
  @JsonProperty("synced") SYNC("sync", "synced"),
  @JsonProperty("merged") MERGED("merge", "merged"),

  // Gitlab
  @JsonProperty("sync") GITLAB_SYNC("sync", "sync"),
  @JsonProperty("open") GITLAB_OPEN("open", "open"),
  @JsonProperty("close") GITLAB_CLOSE("close", "close"),
  @JsonProperty("reopen") GITLAB_REOPEN("reopen", "reopen"),
  @JsonProperty("merge") GITLAB_MERGED("merge", "merge"),
  @JsonProperty("update") GITLAB_UPDATED("update", "update"),
  @JsonProperty("mr comment create") GITLAB_MR_COMMENT_CREATE("create", "create"),

  // BitBucket
  @JsonProperty("pull request created") BT_PULL_REQUEST_CREATED("open", "pull request created"),
  @JsonProperty("pull request updated") BT_PULL_REQUEST_UPDATED("sync", "pull request updated"),
  @JsonProperty("pull request merged") BT_PULL_REQUEST_MERGED("merge", "pull request merged"),
  @JsonProperty("pull request declined") BT_PULL_REQUEST_DECLINED("close", "pull request declined"),
  @JsonProperty("pr comment created") BT_PR_COMMENT_CREATED("create", "pr comment created"),
  @JsonProperty("pr comment edited") BT_PR_COMMENT_EDITED("edit", "pr comment edited"),
  @JsonProperty("pr comment deleted") BT_PR_COMMENT_DELETED("delete", "pr comment deleted");

  // TODO: Add more support for more actions we need to support
  private String value;
  private String parsedValue;

  WebhookAction(String parsedValue, String value) {
    this.parsedValue = parsedValue;
    this.value = value;
    EventActionHolder.map.put(value, this);
  }

  public String getParsedValue() {
    return parsedValue;
  }

  public String getValue() {
    return value;
  }
  private static class EventActionHolder { static Map<String, WebhookAction> map = new HashMap<>(); }

  public static WebhookAction find(String val) {
    WebhookAction action = EventActionHolder.map.get(val);
    if (action == null) {
      throw new InvalidRequestException(String.format("Unsupported Webhook action %s.", val));
    }
    return action;
  }

  public static Set<WebhookAction> getGithubActionForEvent(WebhookEvent event) {
    switch (event) {
      case PULL_REQUEST:
        return EnumSet.of(CLOSED, EDITED, LABELED, OPENED, REOPENED, SYNCHRONIZED, UNLABELED);
      case PUSH:
        return emptySet();
      case ISSUE_COMMENT:
        return EnumSet.of(CREATED, EDITED, DELETED);
      default:
        throw new InvalidRequestException("Event " + event.name() + " not a github event");
    }
  }

  public static Set<WebhookAction> getBitbucketActionForEvent(WebhookEvent event) {
    switch (event) {
      case PULL_REQUEST:
        return EnumSet.of(
            BT_PULL_REQUEST_CREATED, BT_PULL_REQUEST_UPDATED, BT_PULL_REQUEST_MERGED, BT_PULL_REQUEST_DECLINED);
      case PUSH:
        return emptySet();
      case PR_COMMENT:
        return EnumSet.of(BT_PR_COMMENT_CREATED, BT_PR_COMMENT_EDITED, BT_PR_COMMENT_DELETED);
      default:
        throw new InvalidRequestException("Event " + event.name() + " not a bitbucket event");
    }
  }

  public static Set<WebhookAction> getGitLabActionForEvent(WebhookEvent event) {
    switch (event) {
      case MERGE_REQUEST:
        return EnumSet.of(GITLAB_OPEN, GITLAB_CLOSE, GITLAB_REOPEN, GITLAB_MERGED, GITLAB_UPDATED, GITLAB_SYNC);
      case PUSH:
      case DELETE:
        return emptySet();
      case MR_COMMENT:
        return EnumSet.of(GITLAB_MR_COMMENT_CREATE);
      default:
        throw new InvalidRequestException("Event " + event.name() + " not a gitlab event");
    }
  }

  public static Set<WebhookAction> getAwsCodeCommitActionForEvent(WebhookEvent event) {
    switch (event) {
      case BRANCH:
      case TAG:
        return EnumSet.of(CREATED, DELETED);
      case PUSH:
        return emptySet();
      default:
        throw new InvalidRequestException("Event " + event.name() + " not an AWS code commit event");
    }
  }

  public static Set<WebhookAction> getHarnessScmActionForEvent(WebhookEvent event) {
    switch (event) {
      case PULL_REQUEST:
        return EnumSet.of(OPENED, REOPENED, UPDATED);
      case PUSH:
        return emptySet();
      case ISSUE_COMMENT:
        return EnumSet.of(CREATED, EDITED, DELETED);
      default:
        throw new InvalidRequestException("Event " + event.name() + " not a harness scm event");
    }
  }
}

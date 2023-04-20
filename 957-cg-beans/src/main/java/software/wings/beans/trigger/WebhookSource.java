/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
public enum WebhookSource {
  GITHUB,
  GITLAB,
  BITBUCKET,
  AZURE_DEVOPS;

  @OwnedBy(CDC)
  public interface WebhookEvent {}

  public enum GitHubEventType implements WebhookEvent {
    PULL_REQUEST("Pull Request", "pull_request", WebhookEventType.PULL_REQUEST),
    PUSH("Push", "push", WebhookEventType.PUSH),
    PING("Ping", "ping", WebhookEventType.PING),
    DELETE("Delete", "delete", WebhookEventType.DELETE),
    ANY("Any", "any", WebhookEventType.ANY),
    // OTHER("Other", "other", WebhookEventType.OTHER),
    PULL_REQUEST_ANY("Any", "pullrequest_any", WebhookEventType.PULL_REQUEST),
    PULL_REQUEST_CLOSED("Closed", "closed", WebhookEventType.PULL_REQUEST),
    PULL_REQUEST_EDITED("Edited", "edited", WebhookEventType.PULL_REQUEST),
    PULL_REQUEST_OPENED("Opened", "opened", WebhookEventType.PULL_REQUEST),
    PULL_REQUEST_REOPENED("Reopened", "reopened", WebhookEventType.PULL_REQUEST),
    PULL_REQUEST_ASSIGNED("Assigned", "assigned", WebhookEventType.PULL_REQUEST),
    PULL_REQUEST_UNASSIGNED("Unassigned", "unassigned", WebhookEventType.PULL_REQUEST),
    PULL_REQUEST_LABELED("Labeled", "labeled", WebhookEventType.PULL_REQUEST),
    PULL_REQUEST_UNLABELED("Unlabeled", "unlabeled", WebhookEventType.PULL_REQUEST),
    PULL_REQUEST_SYNCHRONIZED("Synchronized", "synchronize", WebhookEventType.PULL_REQUEST),
    PULL_REQUEST_REVIEW_REQUESTED("Review Requested", "review_requested", WebhookEventType.PULL_REQUEST),
    PULL_REQUEST_REVIEW_REQUESTED_REMOVED(
        "Review Request Removed", "review_request_removed", WebhookEventType.PULL_REQUEST),
    RELEASE("Release", "release", WebhookEventType.RELEASE),
    PACKAGE("Package", "package", WebhookEventType.PACKAGE);

    @Getter private String displayName;
    @Getter private String value;
    @Getter private WebhookEventType eventType;

    GitHubEventType(String displayName, String value, WebhookEventType eventType) {
      this.displayName = displayName;
      this.value = value;
      this.eventType = eventType;
      GHEventHolder.map.put(value, this);
    }

    @UtilityClass
    public static class GHEventHolder {
      @Getter static Map<String, GitHubEventType> map = new HashMap<>();
    }

    public static GitHubEventType find(String val) {
      return GitHubEventType.GHEventHolder.map.get(val);
    }
  }

  public enum BitBucketEventType implements WebhookEvent {
    PING("Ping", "ping", null, WebhookEventType.PING, 1),
    DIAGNOSTICS_PING("Diagnostics Ping", "diagnostics:ping", null, WebhookEventType.PING, 1),
    ALL("All", "all", null, WebhookEventType.ANY, 1),

    PUSH_ANY("Any", "repo:any", null, WebhookEventType.PUSH, 1),
    ANY("Any", "any", null, WebhookEventType.ANY, 1),

    // OTHER("Other", "other", WebhookEventType.OTHER),
    FORK("Fork", "repo:fork", null, WebhookEventType.REPO, 3),
    UPDATED("Updated", "repo:updated", "repo:comment:edited", WebhookEventType.REPO, 8),
    COMMIT_COMMENT_CREATED(
        "Commit Comment Created", "repo:commit_comment_created", " repo:comment:added", WebhookEventType.REPO, 3),
    BUILD_STATUS_CREATED("Build Status Created", "repo:commit_status_created", null, WebhookEventType.REPO, 4),
    BUILD_STATUS_UPDATED("Build Status Updated", "repo:commit_status_updated", null, WebhookEventType.REPO, 5),
    PUSH("Push", "repo:push", "repo:refs_changed", WebhookEventType.REPO, 6),
    REFS_CHANGED("Refs Changed", "repo:refs_changed", null, WebhookEventType.REPO, 7),

    ISSUE_ANY("Any", "issue:any", null, WebhookEventType.ISSUE, 1),
    ISSUE_CREATED("Issue Created", "issue:created", null, WebhookEventType.ISSUE, 2),
    ISSUE_UPDATED("Issue Updated", "issue:updated", null, WebhookEventType.ISSUE, 3),
    ISSUE_COMMENT_CREATED("Issue Comment Created", "issue:comment_created", null, WebhookEventType.ISSUE, 4),

    PULL_REQUEST_ANY("Any", "pullrequest:any", null, WebhookEventType.PULL_REQUEST, 1),
    PULL_REQUEST_CREATED("Pull Request Created", "pullrequest:created", "pr:opened", WebhookEventType.PULL_REQUEST, 2),
    PULL_REQUEST_UPDATED(
        "Pull Request Updated", "pullrequest:updated", "pr:modified", WebhookEventType.PULL_REQUEST, 3),
    PULL_REQUEST_APPROVED(
        "Pull Request Approved", "pullrequest:approved", "pr:reviewer:approved", WebhookEventType.PULL_REQUEST, 4),
    PULL_REQUEST_APPROVAL_REMOVED("Pull Request Approval Removed", "pullrequest:unapproved", "pr:reviewer:updated",
        WebhookEventType.PULL_REQUEST, 5),
    PULL_REQUEST_MERGED("Pull Request Merged", "pullrequest:fulfilled", "pr:merged", WebhookEventType.PULL_REQUEST, 6),
    PULL_REQUEST_DECLINED(
        "Pull Request Declined", "pullrequest:rejected", "pr:declined", WebhookEventType.PULL_REQUEST, 7),
    PULL_REQUEST_COMMENT_CREATED("Pull Request Comment Created", "pullrequest:comment_created", "pr:comment:added",
        WebhookEventType.PULL_REQUEST, 8),
    PULL_REQUEST_COMMENT_UPDATED("Pull Request Comment Updated", "pullrequest:comment_updated", "pr:comment:edited",
        WebhookEventType.PULL_REQUEST, 9),
    PULL_REQUEST_COMMENT_DELETED("Pull Request Comment Deleted", "pullrequest:comment_deleted", "pr:comment:deleted",
        WebhookEventType.PULL_REQUEST, 10);

    @Getter private String displayName;
    @Getter private String value;
    @Getter private WebhookEventType eventType;

    BitBucketEventType(
        String displayName, String value, String onPremEventKey, WebhookEventType eventType, int counter) {
      this.displayName = displayName;
      this.value = value;
      this.eventType = eventType;
      BitBucketEventHolder.map.put(value, this);
      if (onPremEventKey != null) {
        BitBucketEventHolder.map.put(onPremEventKey, this);
      }
    }

    public static class BitBucketEventHolder {
      @Getter static Map<String, BitBucketEventType> map = new HashMap<>();
    }

    public static BitBucketEventType find(String val) {
      return BitBucketEventType.BitBucketEventHolder.map.get(val);
    }

    public static boolean containsAllEvent(List<BitBucketEventType> bitBucketEventType) {
      if (bitBucketEventType.contains(ALL) || bitBucketEventType.contains(ANY)) {
        return true;
      } else {
        return false;
      }
    }
  }

  public enum GitLabEventType implements WebhookEvent {
    PULL_REQUEST("Pull Request", "Merge Request Hook", WebhookEventType.PULL_REQUEST),
    PUSH("Push", "Push Hook", WebhookEventType.PUSH),
    PING("Ping", "ping", WebhookEventType.PING),
    ANY("Any", "any", WebhookEventType.ANY);
    // OTHER("Other", "other", WebhookEventType.OTHER);

    @Getter private String displayName;
    @Getter private String value;
    @Getter private WebhookEventType eventType;

    GitLabEventType(String displayName, String eventKeyValue, WebhookEventType eventType) {
      this.displayName = displayName;
      this.value = eventKeyValue;
      this.eventType = eventType;
      GitLabEventHolder.map.put(eventKeyValue, this);
    }

    public static class GitLabEventHolder {
      @Getter static Map<String, GitLabEventType> map = new HashMap<>();
    }

    public static GitLabEventType find(String eventKeyValue) {
      return GitLabEventType.GitLabEventHolder.map.get(eventKeyValue);
    }
  }

  public enum AzureDevopsPullRequestStatus {
    ACTIVE("active"),
    COMPLETED("completed");

    @Getter private String value;

    AzureDevopsPullRequestStatus(String value) {
      this.value = value;
      WebhookSource.AzureDevopsPullRequestStatus.AzureDevOpsPullRequestStatusHolder.map.put(value, this);
    }

    public static class AzureDevOpsPullRequestStatusHolder {
      @Getter static Map<String, AzureDevopsPullRequestStatus> map = new HashMap<>();
    }

    public static AzureDevopsPullRequestStatus find(String eventKeyValue) {
      return AzureDevOpsPullRequestStatusHolder.map.get(eventKeyValue);
    }
  }

  public enum AzureDevOpsEventType implements WebhookEvent {
    PULL_REQUEST_MERGED("Pull request merged", "git.pullrequest.merged", WebhookEventType.PUSH),
    CODE_PUSH("Code Pushed", "git.push", WebhookEventType.PUSH);

    @Getter private String displayName;
    @Getter private String value;
    @Getter private WebhookEventType eventType;

    AzureDevOpsEventType(String displayName, String value, WebhookEventType eventType) {
      this.displayName = displayName;
      this.value = value;
      this.eventType = eventType;
      WebhookSource.AzureDevOpsEventType.AzureDevOpsEventHolder.map.put(value, this);
    }

    public static class AzureDevOpsEventHolder {
      @Getter static Map<String, AzureDevOpsEventType> map = new HashMap<>();
    }

    public static AzureDevOpsEventType find(String eventKeyValue) {
      return AzureDevOpsEventType.AzureDevOpsEventHolder.map.get(eventKeyValue);
    }
  }

  @Value
  @Builder
  public static class WebhookSubEventInfo {
    String displayValue;
    String enumName;
  }

  @Value
  @Builder
  public static class WebhookEventInfo {
    String displayValue;
    String enumName;
    List<WebhookSubEventInfo> subEvents;
  }
}

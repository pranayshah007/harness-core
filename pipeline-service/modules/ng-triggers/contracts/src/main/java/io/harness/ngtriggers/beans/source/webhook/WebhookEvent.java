/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.webhook;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.EnumSet;
import java.util.Set;

@OwnedBy(PIPELINE)
public enum WebhookEvent {
  @JsonProperty("Pull Request") PULL_REQUEST,
  @JsonProperty("Push") PUSH,
  @JsonProperty("Issue Comment") ISSUE_COMMENT,
  @JsonProperty("MR Comment") MR_COMMENT,
  @JsonProperty("PR Comment") PR_COMMENT,
  @JsonProperty("Delete") DELETE,
  @JsonProperty("Merge Request") MERGE_REQUEST,
  @JsonProperty("Repository") REPOSITORY,
  @JsonProperty("Branch") BRANCH,
  @JsonProperty("Tag") TAG,
  @JsonProperty("Release") RELEASE;

  public static final Set<WebhookEvent> githubEvents = EnumSet.of(PULL_REQUEST, PUSH, ISSUE_COMMENT, RELEASE);
  public static final Set<WebhookEvent> gitlabEvents = EnumSet.of(PUSH, MERGE_REQUEST, MR_COMMENT);
  public static final Set<WebhookEvent> bitbucketEvents = EnumSet.of(PULL_REQUEST, PUSH, PR_COMMENT);
  public static final Set<WebhookEvent> awsCodeCommitEvents = EnumSet.of(PUSH);
  // todo: check on supported events
  public static final Set<WebhookEvent> harnessScmEvents = EnumSet.of(PUSH);
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.gitpolling;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.task.gitpolling.github.GitHubPollingDelegateRequest;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class GitDelegateRequestUtils {
  public GitHubPollingDelegateRequest getGitHubDelegateRequest(
      ConnectorDetails connectorDetails, String webhookId, String repository, GitPollingSourceType sourceType) {
    return GitHubPollingDelegateRequest.builder()
        .connectorDetails(connectorDetails)
        .webhookId(webhookId)
        .repository(repository)
        .sourceType(sourceType)
        .build();
  }
}

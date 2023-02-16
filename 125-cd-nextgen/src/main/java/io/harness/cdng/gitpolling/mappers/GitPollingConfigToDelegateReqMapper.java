/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitpolling.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.task.gitpolling.GitDelegateRequestUtils;
import io.harness.delegate.task.gitpolling.GitPollingSourceType;
import io.harness.delegate.task.gitpolling.github.GitHubPollingDelegateRequest;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class GitPollingConfigToDelegateReqMapper {
  public GitHubPollingDelegateRequest getGitHubDelegateRequest(
      ConnectorDetails connectorDetails, String webhookId, String repository) {
    return GitDelegateRequestUtils.getGitHubDelegateRequest(
        connectorDetails, webhookId, repository, GitPollingSourceType.GITHUB);
  }
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.git.taskHandlers.github;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.git.taskHandlers.GitPollingTaskHandler;
import io.harness.delegate.task.gitapi.client.impl.GithubApiClient;
import io.harness.delegate.task.gitpolling.GitPollingSourceType;
import io.harness.delegate.task.gitpolling.github.GitHubPollingDelegateRequest;
import io.harness.delegate.task.gitpolling.response.GitPollingTaskExecutionResponse;
import io.harness.gitpolling.github.GitPollingWebhookData;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDP)
public class GitHubTaskHandler extends GitPollingTaskHandler<GitHubPollingDelegateRequest> {
  private final SecretDecryptionService secretDecryptionService;

  @Inject private GithubApiClient githubApiClient;

  @Override
  public GitPollingTaskExecutionResponse getWebhookRecentDeliveryEvents(
      GitHubPollingDelegateRequest attributesRequest) {
    List<GitPollingWebhookData> gitPollingEventList = githubApiClient.getWebhookRecentDeliveryEvents(attributesRequest);
    return getSuccessExecutionResponse(gitPollingEventList);
  }

  private GitPollingTaskExecutionResponse getSuccessExecutionResponse(List<GitPollingWebhookData> gitPollingEventList) {
    return GitPollingTaskExecutionResponse.builder()
        .gitPollingWebhookEventResponses(gitPollingEventList)
        .gitPollingSourceType(GitPollingSourceType.GITHUB)
        .build();
  }
}

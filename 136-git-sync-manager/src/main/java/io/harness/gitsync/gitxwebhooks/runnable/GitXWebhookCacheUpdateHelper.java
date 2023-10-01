/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.runnable;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.GitSyncModule;
import io.harness.gitsync.gitxwebhooks.dtos.GitXCacheUpdateHelperRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXCacheUpdateRunnableRequestDTO;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class GitXWebhookCacheUpdateHelper {
  private ExecutorService executor;
  private Injector injector;

  @Inject
  public GitXWebhookCacheUpdateHelper(
      @Named(GitSyncModule.GITX_WEBHOOK_HANDLER_EXECUTOR_NAME) ExecutorService executor, Injector injector) {
    this.executor = executor;
    this.injector = injector;
  }

  public void submitTask(String eventIdentifier, GitXCacheUpdateHelperRequestDTO gitXCacheUpdateHelperRequestDTO) {
    try {
      GitXWebhookCacheUpdateRunnable gitXWebhookCacheUpdateRunnable = new GitXWebhookCacheUpdateRunnable(
          eventIdentifier, buildGitXCacheUpdateRunnableDTO(gitXCacheUpdateHelperRequestDTO));
      injector.injectMembers(gitXWebhookCacheUpdateRunnable);
      executor.execute(gitXWebhookCacheUpdateRunnable);
    } catch (Exception exception) {
      log.error("Faced exception while submitting background task for updating the git cache for event: {} ",
          eventIdentifier, exception);
    }
  }

  private GitXCacheUpdateRunnableRequestDTO buildGitXCacheUpdateRunnableDTO(
      GitXCacheUpdateHelperRequestDTO gitXCacheUpdateHelperRequestDTO) {
    return GitXCacheUpdateRunnableRequestDTO.builder()
        .accountIdentifier(gitXCacheUpdateHelperRequestDTO.getAccountIdentifier())
        .branch(gitXCacheUpdateHelperRequestDTO.getBranch())
        .repoName(gitXCacheUpdateHelperRequestDTO.getRepoName())
        .connectorRef(gitXCacheUpdateHelperRequestDTO.getConnectorRef())
        .eventIdentifier(gitXCacheUpdateHelperRequestDTO.getEventIdentifier())
        .modifiedFilePaths(gitXCacheUpdateHelperRequestDTO.getModifiedFilePaths())
        .scmConnector(gitXCacheUpdateHelperRequestDTO.getScmConnector())
        .build();
  }
}

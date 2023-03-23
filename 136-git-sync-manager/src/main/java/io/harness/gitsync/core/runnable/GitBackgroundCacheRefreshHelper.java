/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.runnable;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.GitSyncModule;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.gitsync.core.beans.GitBatchFileFetchRunnableParams;
import io.harness.gitsync.core.beans.GitFileFetchRunnableParams;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class GitBackgroundCacheRefreshHelper {
  ScmFacilitatorService scmFacilitatorService;
  ExecutorService executor;
  Injector injector;

  @Inject
  public GitBackgroundCacheRefreshHelper(ScmFacilitatorService scmFacilitatorService,
      @Named(GitSyncModule.GITX_BACKGROUND_CACHE_UPDATE_EXECUTOR_NAME) ExecutorService executor, Injector injector) {
    this.scmFacilitatorService = scmFacilitatorService;
    this.executor = executor;
    this.injector = injector;
  }

  public void submitTask(GitFileFetchRunnableParams gitFileFetchRunnableParams) {
    try {
      GitFileFetchRunnable gitFileFetchRunnable = new GitFileFetchRunnable(gitFileFetchRunnableParams);
      injector.injectMembers(gitFileFetchRunnable);
      executor.execute(gitFileFetchRunnable);
    } catch (RejectedExecutionException rejectedExecutionException) {
      log.warn("Skipping background cache update as task queue is full : {}", rejectedExecutionException.getMessage());
    } catch (Exception exception) {
      log.error("Faced exception while submitting background cache update task", exception);
    }
  }

  public void submitBatchTask(GitBatchFileFetchRunnableParams gitBatchFileFetchRunnableParams) {
    try {
      GitBatchFileFetchRunnable gitBatchFileFetchRunnable =
          new GitBatchFileFetchRunnable(gitBatchFileFetchRunnableParams);
      injector.injectMembers(gitBatchFileFetchRunnable);
      executor.execute(gitBatchFileFetchRunnable);
    } catch (RejectedExecutionException rejectedExecutionException) {
      log.warn(
          "Skipping background BATCH cache update as task queue is full : {}", rejectedExecutionException.getMessage());
    } catch (Exception exception) {
      log.error("Faced exception while submitting background BATCH cache update task", exception);
    }
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.runnable;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.gitsync.core.beans.GitDefaultBranchCacheRunnableParams;
import io.harness.logging.ResponseTimeRecorder;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class GitDefaultBranchRunnable implements Runnable {
  @Inject private ScmFacilitatorService scmFacilitatorService;
  private GitDefaultBranchCacheRunnableParams gitDefaultBranchCacheRunnableParams;

  public GitDefaultBranchRunnable(GitDefaultBranchCacheRunnableParams gitDefaultBranchCacheRunnableParams) {
    this.gitDefaultBranchCacheRunnableParams = gitDefaultBranchCacheRunnableParams;
  }

  @Override
  public void run() {
    try (ResponseTimeRecorder ignore2 = new ResponseTimeRecorder("GitDefaultBranchRunnable BG Task");) {
      try {
        log.info("Updating default branch cache in BG THREAD");
        scmFacilitatorService.getDefaultBranch(gitDefaultBranchCacheRunnableParams.getAccountIdentifier(),
            gitDefaultBranchCacheRunnableParams.getOrgIdentifier(),
            gitDefaultBranchCacheRunnableParams.getProjectIdentifier(),
            gitDefaultBranchCacheRunnableParams.getScmConnector(), gitDefaultBranchCacheRunnableParams.getRepoName());
        log.info("Successfully updated default branch cache in BG THREAD");
      } catch (WingsException wingsException) {
        log.warn("Error while updating default branch cache in BG THREAD : ", wingsException);
      } catch (Exception exception) {
        log.error("Faced exception while updating default branch cache in BG THREAD : ", exception);
      }
    }
  }
}
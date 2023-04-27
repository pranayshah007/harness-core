/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.runnable;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.dtos.ScmGetFileByBranchRequestDTO;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.gitsync.core.beans.GitFileFetchRunnableParams;
import io.harness.logging.ResponseTimeRecorder;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class GitFileFetchRunnable implements Runnable {
  @Inject private ScmFacilitatorService scmFacilitatorService;
  private GitFileFetchRunnableParams gitFileFetchRunnableParams;

  public GitFileFetchRunnable(GitFileFetchRunnableParams gitFileFetchRunnableParams) {
    this.gitFileFetchRunnableParams = gitFileFetchRunnableParams;
  }

  @Override
  public void run() {
    try (ResponseTimeRecorder ignore2 = new ResponseTimeRecorder("GitFileFetchRunnable BG Task");) {
      try {
        log.info("Fetching file content from GIT in BG THREAD");
        scmFacilitatorService.getFileByBranchV2(ScmGetFileByBranchRequestDTO.builder()
                                                    .filePath(gitFileFetchRunnableParams.getFilePath())
                                                    .branchName(gitFileFetchRunnableParams.getBranchName())
                                                    .commitId(gitFileFetchRunnableParams.getCommitId())
                                                    .connectorRef(gitFileFetchRunnableParams.getConnectorRef())
                                                    .repoName(gitFileFetchRunnableParams.getRepoName())
                                                    .scope(gitFileFetchRunnableParams.getScope())
                                                    .scmConnector(gitFileFetchRunnableParams.getScmConnector())
                                                    .build());
        log.info("Successfully fetched file from GIT in BG THREAD");
      } catch (WingsException wingsException) {
        log.warn("Error while fetching file from GIT in BG THREAD : ", wingsException);
      } catch (Exception exception) {
        log.error("Faced exception while fetching file from GIT in BG THREAD : ", exception);
      }
    }
  }
}

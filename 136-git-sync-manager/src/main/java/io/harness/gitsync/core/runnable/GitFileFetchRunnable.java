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
import io.harness.gitsync.common.beans.GitOperation;
import io.harness.gitsync.common.dtos.ScmGetFileByBranchRequestDTO;
import io.harness.gitsync.common.helper.GitSyncLogContextHelper;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.gitsync.core.beans.GitFileFetchRunnableParams;
import io.harness.logging.MdcContextSetter;
import io.harness.logging.ResponseTimeRecorder;
import io.harness.manage.GlobalContextManager;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class GitFileFetchRunnable implements Runnable {
  ScmFacilitatorService scmFacilitatorService;
  private final GitFileFetchRunnableParams gitFileFetchRunnableParams;

  public GitFileFetchRunnable(GitFileFetchRunnableParams gitFileFetchRunnableParams) {
    this.gitFileFetchRunnableParams = gitFileFetchRunnableParams;
    this.scmFacilitatorService = gitFileFetchRunnableParams.getScmFacilitatorService();
  }

  @Override
  public void run() {
    Map<String, String> contextMap = GitSyncLogContextHelper.setContextMap(gitFileFetchRunnableParams.getScope(),
        gitFileFetchRunnableParams.getRepoName(), gitFileFetchRunnableParams.getBranchName(),
        gitFileFetchRunnableParams.getFilePath(), GitOperation.BG_THREAD_GET_FILE, null);
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard();
         MdcContextSetter ignore1 = new MdcContextSetter(contextMap);
         ResponseTimeRecorder ignore2 = new ResponseTimeRecorder("GitFileFetchRunnable BG Task");) {
      try {
        log.info("Fetching file content from GIT in BG THREAD");
        scmFacilitatorService.getFileByBranchV2(ScmGetFileByBranchRequestDTO.builder()
                                                    .filePath(gitFileFetchRunnableParams.getFilePath())
                                                    .branchName(gitFileFetchRunnableParams.getBranchName())
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

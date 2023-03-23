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
import io.harness.gitsync.common.dtos.ScmGetBatchFileRequestIdentifier;
import io.harness.gitsync.common.dtos.ScmGetBatchFilesByBranchRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileByBranchRequestDTO;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.gitsync.core.beans.GitBatchFileFetchRunnableParams;
import io.harness.logging.ResponseTimeRecorder;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class GitBatchFileFetchRunnable implements Runnable {
  @Inject private ScmFacilitatorService scmFacilitatorService;
  private final GitBatchFileFetchRunnableParams gitBatchFileFetchRunnableParams;

  public GitBatchFileFetchRunnable(GitBatchFileFetchRunnableParams gitBatchFileFetchRunnableParams) {
    this.gitBatchFileFetchRunnableParams = gitBatchFileFetchRunnableParams;
  }

  @Override
  public void run() {
    try (ResponseTimeRecorder ignore2 = new ResponseTimeRecorder("GitBatchFileFetchRunnable BG Task");) {
      Map<ScmGetBatchFileRequestIdentifier, ScmGetFileByBranchRequestDTO> scmGetFileByBranchRequestDTOMap =
          new HashMap<>();
      gitBatchFileFetchRunnableParams.getGitFileFetchRunnableParamsMap().forEach((requestIdentifier, request) -> {
        scmGetFileByBranchRequestDTOMap.put(
            ScmGetBatchFileRequestIdentifier.builder().identifier(requestIdentifier.getIdentifier()).build(),
            ScmGetFileByBranchRequestDTO.builder()
                .scmConnector(request.getScmConnector())
                .scope(request.getScope())
                .connectorRef(request.getConnectorRef())
                .filePath(request.getFilePath())
                .repoName(request.getRepoName())
                .branchName(request.getBranchName())
                .build());
      });

      scmFacilitatorService.getBatchFilesByBranch(
          ScmGetBatchFilesByBranchRequestDTO.builder()
              .accountIdentifier(gitBatchFileFetchRunnableParams.getAccountIdentifier())
              .scmGetFileByBranchRequestDTOMap(scmGetFileByBranchRequestDTOMap)
              .build());
    } catch (WingsException wingsException) {
      log.warn("Error while doing batch fetch file from GIT in BG THREAD : ", wingsException);
    } catch (Exception exception) {
      log.error("Faced exception while doing batch fetch file from GIT in BG THREAD : ", exception);
    }
  }
}

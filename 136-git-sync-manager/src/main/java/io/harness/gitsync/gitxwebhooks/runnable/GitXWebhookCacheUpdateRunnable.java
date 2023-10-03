/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.runnable;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.gitsync.common.beans.GitXWebhookEventStatus;
import io.harness.gitsync.common.dtos.ScmGetBatchFileRequestIdentifier;
import io.harness.gitsync.common.dtos.ScmGetFileByBranchRequestDTO;
import io.harness.gitsync.common.dtos.ScmUpdateGitCacheRequestDTO;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.gitsync.gitxwebhooks.dtos.GitXCacheUpdateRunnableRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventUpdateRequestDTO;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookEventService;
import io.harness.logging.ResponseTimeRecorder;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class GitXWebhookCacheUpdateRunnable implements Runnable {
  @Inject private ScmFacilitatorService scmFacilitatorService;
  @Inject private GitXWebhookEventService gitXWebhookEventService;
  private GitXCacheUpdateRunnableRequestDTO gitXCacheUpdateRunnableRequestDTO;
  private String eventIdentifier;

  public GitXWebhookCacheUpdateRunnable(
      String eventIdentifier, GitXCacheUpdateRunnableRequestDTO gitXCacheUpdateRunnableRequestDTO) {
    this.eventIdentifier = eventIdentifier;
    this.gitXCacheUpdateRunnableRequestDTO = gitXCacheUpdateRunnableRequestDTO;
  }

  @Override
  public void run() {
    try (ResponseTimeRecorder ignore2 = new ResponseTimeRecorder("GitXWebhookCacheUpdateRunnable BG Task")) {
      scmFacilitatorService.updateGitCache(buildScmUpdateGitCacheRequestDTO(gitXCacheUpdateRunnableRequestDTO));
      gitXWebhookEventService.updateEvent(gitXCacheUpdateRunnableRequestDTO.getAccountIdentifier(), eventIdentifier,
          GitXEventUpdateRequestDTO.builder().gitXWebhookEventStatus(GitXWebhookEventStatus.SUCCESSFUL).build());
    } catch (Exception exception) {
      gitXWebhookEventService.updateEvent(gitXCacheUpdateRunnableRequestDTO.getAccountIdentifier(), eventIdentifier,
          GitXEventUpdateRequestDTO.builder().gitXWebhookEventStatus(GitXWebhookEventStatus.FAILED).build());
      log.error("Faced exception while submitting background task for updating the git cache for event: {} ",
          eventIdentifier, exception);
    }
  }

  private ScmUpdateGitCacheRequestDTO buildScmUpdateGitCacheRequestDTO(
      GitXCacheUpdateRunnableRequestDTO gitXCacheUpdateRunnableRequestDTO) {
    return ScmUpdateGitCacheRequestDTO.builder()
        .accountIdentifier(gitXCacheUpdateRunnableRequestDTO.getAccountIdentifier())
        .scmGetFileByBranchRequestDTOMap(buildScmGetFileByBranchRequestDTOMap(gitXCacheUpdateRunnableRequestDTO))
        .build();
  }

  private Map<ScmGetBatchFileRequestIdentifier, ScmGetFileByBranchRequestDTO> buildScmGetFileByBranchRequestDTOMap(
      GitXCacheUpdateRunnableRequestDTO gitXCacheUpdateRunnableRequestDTO) {
    Map<ScmGetBatchFileRequestIdentifier, ScmGetFileByBranchRequestDTO> scmGetFileByBranchRequestDTOMap =
        new HashMap<>();
    gitXCacheUpdateRunnableRequestDTO.getModifiedFilePaths().forEach(modifiedFilePath -> {
      String uniqueIdentifier = buildUniqueIdentifier(gitXCacheUpdateRunnableRequestDTO, modifiedFilePath);
      ScmGetBatchFileRequestIdentifier scmGetBatchFileRequestIdentifier =
          ScmGetBatchFileRequestIdentifier.builder().identifier(uniqueIdentifier).build();
      ScmGetFileByBranchRequestDTO scmGetFileByBranchRequestDTO =
          ScmGetFileByBranchRequestDTO.builder()
              .scope(Scope.of(gitXCacheUpdateRunnableRequestDTO.getAccountIdentifier()))
              .scmConnector(gitXCacheUpdateRunnableRequestDTO.getScmConnector())
              .repoName(gitXCacheUpdateRunnableRequestDTO.getRepoName())
              .branchName(gitXCacheUpdateRunnableRequestDTO.getBranch())
              .filePath(modifiedFilePath)
              .connectorRef(gitXCacheUpdateRunnableRequestDTO.getConnectorRef())
              .useCache(false)
              .build();
      scmGetFileByBranchRequestDTOMap.put(scmGetBatchFileRequestIdentifier, scmGetFileByBranchRequestDTO);
    });
    return scmGetFileByBranchRequestDTOMap;
  }

  private String buildUniqueIdentifier(
      GitXCacheUpdateRunnableRequestDTO gitXCacheUpdateRunnableRequestDTO, String modifiedFilePath) {
    return gitXCacheUpdateRunnableRequestDTO.getAccountIdentifier() + "/"
        + gitXCacheUpdateRunnableRequestDTO.getEventIdentifier() + "/" + modifiedFilePath;
  }
}

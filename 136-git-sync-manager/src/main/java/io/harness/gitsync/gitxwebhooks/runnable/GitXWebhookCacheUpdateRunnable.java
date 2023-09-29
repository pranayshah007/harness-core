/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.runnable;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.beans.GitXWebhookEventStatus;
import io.harness.gitsync.common.dtos.ScmGetBatchFilesByBranchRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetBatchFilesResponseDTO;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventUpdateRequestDTO;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookEventService;
import io.harness.logging.ResponseTimeRecorder;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class GitXWebhookCacheUpdateRunnable implements Runnable {
  @Inject private ScmFacilitatorService scmFacilitatorService;
  @Inject private GitXWebhookEventService gitXWebhookEventService;
  private ScmGetBatchFilesByBranchRequestDTO scmGetBatchFilesByBranchRequestDTO;
  private String eventIdentifier;

  public GitXWebhookCacheUpdateRunnable(
      String eventIdentifier, ScmGetBatchFilesByBranchRequestDTO scmGetBatchFilesByBranchRequestDTO) {
    this.eventIdentifier = eventIdentifier;
    this.scmGetBatchFilesByBranchRequestDTO = scmGetBatchFilesByBranchRequestDTO;
  }

  @Override
  public void run() {
    try (ResponseTimeRecorder ignore2 = new ResponseTimeRecorder("GitXWebhookCacheUpdateRunnable BG Task")) {
      ScmGetBatchFilesResponseDTO scmGetBatchFilesResponseDTO =
          scmFacilitatorService.getBatchFilesByBranch(scmGetBatchFilesByBranchRequestDTO);
      gitXWebhookEventService.updateEvent(scmGetBatchFilesByBranchRequestDTO.getAccountIdentifier(), eventIdentifier,
          GitXEventUpdateRequestDTO.builder().gitXWebhookEventStatus(GitXWebhookEventStatus.SUCCESSFUL).build());
    } catch (Exception exception) {
      gitXWebhookEventService.updateEvent(scmGetBatchFilesByBranchRequestDTO.getAccountIdentifier(), eventIdentifier,
          GitXEventUpdateRequestDTO.builder().gitXWebhookEventStatus(GitXWebhookEventStatus.FAILED).build());
      log.error("Faced exception while submitting background task for updating the git cache for event: {} ",
          eventIdentifier, exception);
    }
  }
}
